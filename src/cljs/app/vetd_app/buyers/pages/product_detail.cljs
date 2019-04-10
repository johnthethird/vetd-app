(ns vetd-app.buyers.pages.product-detail
  (:require [vetd-app.buyers.components :as bc]
            [vetd-app.common.components :as cc]
            [vetd-app.ui :as ui]
            [vetd-app.util :as util]
            [vetd-app.docs :as docs]
            [reagent.core :as r]
            [reagent.format :as format]
            [re-frame.core :as rf]
            [clojure.string :as s]))

(def last-query-id (atom 0))

(defn get-next-query-id []
  (swap! last-query-id inc))

;; Events
(rf/reg-event-fx
 :b/nav-product-detail
 (fn [_ [_ product-idstr]]
   {:nav {:path (str "/b/products/" product-idstr)}}))

(rf/reg-event-fx
 :b/route-product-detail
 (fn [{:keys [db]} [_ product-idstr]]
   {:db (assoc db
               :page :b/product-detail
               :page-params {:product-idstr product-idstr})
    :analytics/page {:name "Buyers Product Detail"
                     :props {:product-idstr product-idstr}}}))

(rf/reg-event-fx
 :b/request-complete-profile
 (fn [{:keys [db]} [_ etype eid ename]]
   (let [qid (get-next-query-id)]
     {:ws-send {:payload {:cmd :b/request-complete-profile
                          :return {:handler :b/request-complete-profile-return}
                          :etype etype
                          :eid eid
                          :buyer-id (util/db->current-org-id db)}}
      :analytics/track {:event "Request"
                        :props {:category (str (s/capitalize (name etype)) " Profile")
                                :label ename}}})))

(rf/reg-event-fx
 :b/request-complete-profile-return
 (constantly
  {:toast {:type "success"
           :title "Complete Profile Requested"
           :message "We'll let you know when the profile is completed."}}))

(rf/reg-event-fx
 :b/setup-call
 (fn [{:keys [db]} [_ product-id product-name]]
   (let [qid (get-next-query-id)]
     {:ws-send {:payload {:cmd :b/setup-call
                          :return {:handler :b/setup-call-return}
                          :product-id product-id
                          :buyer-id (util/db->current-org-id db)}}
      :analytics/track {:event "Setup Call"
                        :props {:category "Product"
                                :label product-name}}})))

(rf/reg-event-fx
 :b/setup-call-return
 (constantly
  {:toast {:type "success"
           :title "Setup a Call"
           :message "We'll setup a call for you soon."}}))

(rf/reg-event-fx
 :b/ask-a-question
 (fn [{:keys [db]} [_ product-id product-name message]]
   (let [qid (get-next-query-id)]
     {:ws-send {:payload {:cmd :b/ask-a-question
                          :return {:handler :b/ask-a-question-return}
                          :product-id product-id
                          :message message
                          :buyer-id (util/db->current-org-id db)}}
      :analytics/track {:event "Ask A Question"
                        :props {:category "Product"
                                :label product-name}}})))

(rf/reg-event-fx
 :b/ask-a-question-return
 (constantly
  {:toast {:type "success"
           :title "Question Sent!"
           :message "We'll get an answer for you soon."}}))

;; Subscriptions
(rf/reg-sub
 :product-idstr
 :<- [:page-params] 
 (fn [{:keys [product-idstr]}] product-idstr))

;; Components
(defn c-preposal-request-button
  [{:keys [vendor forms] :as product}]
  (if (not-empty forms) ; already requested preposal
    [:> ui/Popup
     {:content "We will be in touch with next steps."
      :header "Preposal Requested!"
      :position "bottom left"
      :trigger (r/as-element
                [:> ui/Label {:color "teal"
                              :size "large"
                              :basic true
                              :style {:display "block"
                                      :text-align "center"}}
                 "Preposal Requested"])}]
    [:> ui/Popup
     {:content (str "Get a pricing estimate, personalized pitch, and more from "
                    (:oname vendor) ".")
      :header "What is a Preposal?"
      :position "bottom left"
      :trigger (r/as-element
                [:> ui/Button {:onClick #(rf/dispatch [:b/create-preposal-req product vendor])
                               :color "teal"
                               :fluid true
                               :icon true
                               :labelPosition "left"}
                 "Request Preposal"
                 [:> ui/Icon {:name "wpforms"}]])}]))

(defn c-page []
  (let [product-idstr& (rf/subscribe [:product-idstr])
        org-id& (rf/subscribe [:org-id])
        products& (rf/subscribe [:gql/sub
                                 {:queries
                                  [[:products {:idstr @product-idstr&}
                                    [:id :pname :logo
                                     [:form-docs {:ftype "product-profile"
                                                  :_order_by {:created :desc}
                                                  :_limit 1}
                                      [:id 
                                       [:responses
                                        [:id :prompt-id :notes
                                         [:prompt
                                          [:id :prompt]]
                                         [:fields
                                          [:id :pf-id :idx :sval :nval :dval
                                           [:prompt-field [:id :fname]]]]]]]]
                                     [:vendor
                                      [:id :oname :url
                                       [:docs-out {:dtype "vendor-profile"
                                                   :_order_by {:created :desc}
                                                   :_limit 1}
                                        [:id 
                                         [:responses
                                          [:id :prompt-id :notes
                                           [:prompt
                                            [:id :prompt]]
                                           [:fields
                                            [:id :pf-id :idx :sval :nval :dval
                                             [:prompt-field [:id :fname]]]]]]]]]]
                                     [:forms {:ftype "preposal" ; preposal requests
                                              :from-org-id @org-id&}
                                      [:id]]
                                     [:rounds {:buyer-id @org-id&
                                               :status "active"}
                                      [:id :created :status]]
                                     [:categories [:id :idstr :cname]]]]]}])]
    (fn []
      [:div.container-with-sidebar
       [:div.sidebar
        [:div {:style {:padding "0 15px"}}
         [bc/c-back-button {:on-click #(rf/dispatch [:b/nav-search])}
          "Back to Search"]]
        (when-not (= :loading @products&)
          (let [{:keys [vendor rounds] :as product} (-> @products& :products first)]
            (when (empty? (:rounds product))
              [:> ui/Segment
               [bc/c-start-round-button {:etype :product
                                         :eid (:id product)
                                         :ename (:pname product)
                                         :props {:fluid true}}]
               [c-preposal-request-button product]
               [bc/c-setup-call-button product vendor]
               [bc/c-ask-a-question-button product vendor]])))]
       [:div.inner-container
        (if (= :loading @products&)
          [cc/c-loader]
          (let [product (-> @products& :products first)
                {:keys [docs-out id oname]} (:vendor product)]
            [:<>
             [bc/c-product product]
             [bc/c-vendor-profile (first docs-out) id oname]]))]])))
