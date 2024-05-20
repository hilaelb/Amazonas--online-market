package com.amazonas.business.userProfiles;

import com.amazonas.business.inventory.Product;

public interface UsersController {

    void register(String id,String email, String userName, String password);

    void enterAsGuest();

    void loginToRegistered(String id);

    void logout(String id);

    void logoutAsGuest(String id);

    ShoppingCart getCart(String id);

    void addProductToCart(String id, String storeName, Product product, int quantity);

    void RemoveProductFromCart(String id,String storeName,String productId);

    void changeProductQuantity(String id, String storeName, String productId, int quantity);
}
