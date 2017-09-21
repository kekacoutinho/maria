(ns maria.views.top-bar
  (:require [re-view.core :as v :refer [defview]]
            [lark.commands.registry :refer-macros [defcommand]]
            [lark.commands.exec :as exec]
            [maria.views.text :as text]
            [maria.frames.frame-communication :as frame]
            [maria.views.icons :as icons]
            [re-db.d :as d]
            [maria.commands.doc :as doc]
            [maria.util :as util]
            [lark.commands.registry :as registry]
            [goog.events :as events]
            [goog.functions :as gf]
            [maria.persistence.local :as local]))

(defn toolbar-icon [icon]
  (icons/size icon 20))

(defn toolbar-button [[action icon text tooltip]]
  [(if (:href action) :a.no-underline :div)
   (cond-> {:class (str "pa2 flex items-center gray "
                        (when action "pointer hover-black hover-bg-near-white"))}
           tooltip (assoc :data-tooltip (pr-str tooltip))
           (fn? action) (assoc :on-click action)
           (map? action) (merge action))
   (some-> icon (toolbar-icon))
   (when (and icon text) util/space)
   (cond->>
     text
     icon (conj [:.dn.dib-ns]))])

(defn command-button
  [context command-name {:keys [icon else-icon tooltip text]}]
  (let [tooltip (registry/spaced-name (name command-name))
        {:keys [exec? parsed-bindings]} (exec/get-command context command-name)
        key-string (some-> (ffirst parsed-bindings) (registry/keyset-string))]
    (if exec?
      (toolbar-button [#(do (util/stop! %)
                            (exec/exec-command-name command-name)) icon text (if key-string
                                                                               [:.flex.items-center tooltip
                                                                                [:.gray.o-70.ml1 key-string]]
                                                                               tooltip)])
      (when else-icon
        (toolbar-button [nil else-icon nil tooltip])))))


(defview fixed-top
  {:view/did-mount    (fn [{:keys [view/state]}]
                        (->> (events/listen js/window "scroll"
                                            (gf/throttle (fn [e]
                                                           (swap! state assoc :scrolled? (not= 0 (.-scrollY js/window)))) 300))
                             (v/swap-silently! state assoc :listener-key)))
   :view/will-unmount #(events/unlistenByKey (:listener-key @(:view/state %)))}
  [{:keys [when-scrolled view/state]} child]
  [:.fixed.top-0.left-0.right-0.z-5
   (when (:scrolled? @state) when-scrolled) child])

(defview doc-toolbar
  {:view/did-mount          (fn [this]
                              (.updateWindowTitle this)
                              (exec/set-context! {:current-doc this})
                              (some->> (:id this) (doc/locals-push! :local/recents)))
   :view/will-unmount       #(exec/set-context! {:current-doc nil})
   :view/will-receive-props (fn [{filename                                 :filename
                                  props                                    :view/props
                                  {prev-filename :filename :as prev-props} :view/prev-props
                                  :as                                      this}]
                              (when-not (= filename prev-filename)
                                (.updateWindowTitle this))
                              (when (not= props prev-props)
                                (some->> (:id this)
                                         (doc/locals-push! :local/recents))))
   :get-filename            (fn [{:keys [filename] :as this}]
                              (get-in this [:project :local :files filename :filename] filename))
   :update-window-title     (fn [{:keys [view/state] :as this}]
                              (let [filename (.getFilename this)]
                                (when (= filename "Untitled.cljs")
                                  (js/setTimeout #(some-> (:title-input @state)
                                                          :view/state
                                                          (deref)
                                                          :input-element
                                                          (.select)) 50))
                                (frame/send frame/trusted-frame [:window/set-title (util/some-str filename)])))}
  [{{:keys [persisted local]} :project
    :keys                     [filename id view/state left-content] :as this}]
  (let [signed-in? (d/get :auth-public :signed-in?)
        {parent-url :local-url parent-username :username} (or (:owner persisted)
                                                              (:owner this))
        current-filename (.getFilename this)
        {local-content :content} (get-in local [:files filename])
        {persisted-content :content} (get-in persisted [:files filename])
        update-filename #(d/transact! [[:db/update-attr id :local (fn [local]
                                                                    (assoc-in local [:files filename] {:filename %
                                                                                                       :content  (or local-content persisted-content)}))]])
        command-context (exec/get-context)]
    [:div
     (fixed-top
       {:when-scrolled {:style {:background-color "#e7e7e7"
                                :border-bottom    "2px solid #e2e2e2"}}}
       [:.flex.sans-serif.items-stretch.br.f7.flex-none.overflow-hidden.pl2
        (toolbar-button [{:href "/"} icons/Home nil "Home"])
        (command-button command-context :doc/new {:icon icons/Add})

        (some->>
          (or left-content
              (when filename
                (list
                  [:.ph2.flex.items-center
                   (when (and parent-username parent-url)
                     [:a.hover-underline.gray.no-underline.dn.dib-ns {:href parent-url} parent-username])
                   [:.ph1.gray.dn.dib-ns "/"]
                   (text/autosize-text {:auto-focus  true
                                        :class       "mr2 half-b sans-serif"
                                        :ref         #(when % (swap! state assoc :title-input %))
                                        :value       (doc/strip-clj-ext current-filename)
                                        :on-key-down #(cond (and (= 13 (.-which %))
                                                                 (not (or (.-metaKey %)
                                                                          (.-ctrlKey %))))
                                                            (doc/persist! this)
                                                            (= 40 (.-which %))
                                                            (exec/exec-command-name :navigate/focus-start)
                                                            :else nil)
                                        :placeholder "Enter a title..."
                                        :on-change   #(update-filename (doc/add-clj-ext (.-value (.-target %))))})]
                  (command-button command-context :doc/save {:icon      icons/Backup
                                                             :else-icon (when signed-in? (icons/class icons/Backup "o-30"))})
                  (command-button command-context :doc/save-a-copy {:icon icons/ContentDuplicate})

                  (command-button command-context :doc/revert {:icon (update-in icons/Replay [1 :style] assoc
                                                                                :transform "scaleX(-1) rotate(-90deg)")
                                                               :text "Reset"}))))
          (conj [:.flex.items-stretch.bg-darken-lightly]))

        [:.flex-auto]

        (command-button command-context :commands/command-search {:icon icons/Search :text "Commands..."})

        (toolbar-button [{:href      "https://www.github.com/mhuebert/maria/issues"
                          :tab-index -1
                          :target    "_blank"} icons/Bug "Bug Report"])
        (if signed-in? (toolbar-button [#(doc/send [:auth/sign-out]) icons/SignOut nil "Sign out"])
                       (toolbar-button [#(frame/send frame/trusted-frame [:auth/sign-in]) nil "Sign in with GitHub"]))
        [:.ph1]])
     [:.h2]
     ]))

;; (str "/gists/" (d/get :auth-public :username))