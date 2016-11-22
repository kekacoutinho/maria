(ns maria.core
  (:require

    [maria.walkthrough :as walkthrough]
    [maria.views.repl :as repl]
    [maria.tree.paredit :as paredit]


    ;; include to precompile for self-hosted env
    [clojure.set]
    [clojure.string]
    [clojure.walk]
    [cljs.spec :include-macros true]
    [maria.html]
    [maria.user :include-macros true]

    [re-view.subscriptions :refer [router]]
    [re-view.core :as v :refer [defcomponent]]))

(enable-console-print!)

(defcomponent not-found
  :render
  (fn [] [:div "We couldn't find this page!"]))

(defcomponent layout
  :subscriptions {:main-view (router "/" repl/main
                                     "/walkthrough" walkthrough/main
                                     "/paredit" paredit/examples
                                     not-found)}
  :render
  (fn [{{:keys [main-view]} :state}]
    [:div.h-100
     [:.w-100.fixed.bottom-0.z-3
      [:.dib.center
       (for [[href title] [["/" "REPL"]
                           ["/walkthrough" "Walkthrough"]
                           ["/paredit" "Paredit"]]]
         [:a.dib.pa2.black-70.no-underline.f6.bg-black-05 {:href href} title])]]
     (main-view) ]))

(defn main []
  (v/render-to-dom (layout {:x 1}) "maria-main"))

(main)