package com.amazonas.business.permissions.proxies;

import com.amazonas.business.authentication.AuthenticationController;
import com.amazonas.business.inventory.Product;
import com.amazonas.business.permissions.PermissionsController;
import com.amazonas.business.userProfiles.ShoppingCart;
import com.amazonas.business.userProfiles.StoreBasket;
import com.amazonas.business.userProfiles.User;
import com.amazonas.business.userProfiles.UsersController;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("userProxy")
public class UserProxy extends ControllerProxy implements UsersController {

    private final UsersController real;

    public UserProxy(UsersController usersController, PermissionsController perm, AuthenticationController auth) {
        super(perm,auth);
        this.real = usersController;
    }

    @Override
    public void register(String id, String email, String userName, String password) {

    }

    @Override
    public void enterAsGuest() {

    }

    @Override
    public void loginToRegistered(String id) {

    }



    @Override
    public void logout(String id) {

    }

    @Override
    public void logoutAsGuest(String id) {

    }

    @Override
    public ShoppingCart getCart(String id) {
        return null;
    }

    @Override
    public void addProductToCart(String id, String storeName, Product product, int quantity) {

    }

    @Override
    public void RemoveProductFromCart(String id,String storeName,String productId) {

    }

    @Override
    public void changeProductQuantity(String id, String storeName, String productId, int quantity) {

    }
}
