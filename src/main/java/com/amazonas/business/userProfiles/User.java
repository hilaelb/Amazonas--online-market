package com.amazonas.business.userProfiles;

import com.amazonas.business.inventory.Product;
import com.amazonas.business.payment.PaymentMethod;

public abstract class User {

    private String initialId;
    private ShoppingCart cart;
    private PaymentMethod paymentMethod;

    public User(String initialId){
        this.initialId = initialId;

    }

    public ShoppingCart getCart() {return null;}


    public String getUserId(){
        return initialId;
    }
    public PaymentMethod getPaymentMethod() {return paymentMethod;}
}
