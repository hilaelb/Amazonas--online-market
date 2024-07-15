package com.amazonas.backend.business.userProfiles;

import com.amazonas.backend.business.authentication.AuthenticationController;
import com.amazonas.backend.business.authentication.UserCredentials;
import com.amazonas.backend.business.notifications.NotificationController;
import com.amazonas.backend.business.payment.PaymentService;
import com.amazonas.backend.business.permissions.PermissionsController;
import com.amazonas.backend.business.stores.Store;
import com.amazonas.backend.business.stores.reservations.Reservation;
import com.amazonas.common.dtos.Transaction;
import com.amazonas.backend.exceptions.NotificationException;
import com.amazonas.backend.exceptions.PurchaseFailedException;
import com.amazonas.backend.exceptions.ShoppingCartException;
import com.amazonas.backend.exceptions.UserException;
import com.amazonas.backend.repository.*;
import com.amazonas.common.dtos.Product;
import com.amazonas.common.utils.ReadWriteLock;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.amazonas.backend.business.authentication.AuthenticationController.generatePassword;

@Component("usersController")
public class UsersController {
    private static final Logger log = LoggerFactory.getLogger(UsersController.class);
    private final PaymentService paymentService;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final TransactionRepository transactionRepository;
    private final ShoppingCartRepository shoppingCartRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final ShoppingCartFactory shoppingCartFactory;
    private final AuthenticationController authenticationController;
    private final PermissionsController permissionsController;
    private final NotificationController notificationController;

    private final Map<String, ShoppingCart> guestCarts;
    private final Map<String,Guest> guests;
    private final Map<String,User> onlineRegisteredUsers;

    private final ReadWriteLock lock;

    public UsersController(UserRepository userRepository,
                           ReservationRepository reservationRepository,
                           TransactionRepository transactionRepository,
                           ProductRepository productRepository,
                           PaymentService paymentService,
                           ShoppingCartFactory shoppingCartFactory,
                           AuthenticationController authenticationController,
                           ShoppingCartRepository shoppingCartRepository,
                           PermissionsController permissionsController,
                           NotificationController notificationController, StoreRepository storeRepository) {
        this.userRepository = userRepository;
        this.paymentService = paymentService;
        this.shoppingCartFactory = shoppingCartFactory;
        this.reservationRepository = reservationRepository;
        this.transactionRepository = transactionRepository;
        this.productRepository = productRepository;
        this.authenticationController = authenticationController;
        this.shoppingCartRepository = shoppingCartRepository;
        this.permissionsController = permissionsController;

        guests = new HashMap<>();
        onlineRegisteredUsers = new HashMap<>();
        guestCarts = new HashMap<>();
        lock = new ReadWriteLock();
        this.notificationController = notificationController;
        this.storeRepository = storeRepository;
    }

    //generate admin user
    @EventListener
    public void handleApplicationReadyEvent(ApplicationReadyEvent event) {
        String adminId = "admin";
        String adminEmail = "admin@amazonas.com";
        String adminPassword = generatePassword();
        System.out.println("Admin password: " + adminPassword);
        try {
            register(adminEmail, adminId, adminPassword, LocalDate.now().minusYears(22));
        } catch (UserException _) {
            log.debug("admin already exists in the system, updating password");
            authenticationController.updateUser(new UserCredentials(adminId, adminPassword));
        } finally{
            log.debug("Registering admin as an admin");
            permissionsController.registerAdmin(adminId);
        }
    }

    // =============================================================================== |
    // ================================ USER MANAGEMENT ============================== |
    // =============================================================================== |


    public void register(String email, String userId, String password, LocalDate birthDate) throws UserException {

        userId = userId.toLowerCase();
        email = email.toLowerCase();

        if(!isValidUserId(userId)){
            log.debug("User with id: {} already exists in the system", userId);
            throw new UserException("This user name is already exists in the system");
        }

        if (!isValidPassword(password)) {
            log.debug("Password does not meet the requirements");
            throw new UserException("Password must contain at least one uppercase letter and one special character.");
        }

        if (!isValidEmail(email)) {
            log.debug("Invalid email address");
            throw new UserException("Invalid email address.");
        }

        if (!isValidBirthDate(birthDate)) {
            log.debug("Invalid birth date");
            throw new UserException("Invalid birth date.");
        }

        RegisteredUser newRegisteredUser = new RegisteredUser(userId,email, birthDate);
        userRepository.save(newRegisteredUser);
        authenticationController.createUser(new UserCredentials(userId, password));
        permissionsController.registerUser(userId);
        log.debug("User with id: {} registered successfully", userId);
    }

    public String enterAsGuest() {
        try{
            String guestInitialId = UUID.randomUUID().toString();
            Guest newGuest = new Guest(guestInitialId);

            lock.acquireWrite();
            guests.put(guestInitialId,newGuest);
            guestCarts.put(guestInitialId,shoppingCartFactory.get(guestInitialId));
            authenticationController.createGuest(guestInitialId);
            permissionsController.registerGuest(guestInitialId);
            log.debug("Guest with id: {} entered the market", guestInitialId);
            return guestInitialId;
        }
        finally {
            lock.releaseWrite();
        }

    }

    @Transactional
    public void loginToRegistered(String guestInitialId,String userId) throws UserException {
        userId = userId.toLowerCase();

        if(! userRepository.existsById(userId)){
            log.debug("User with id: {} does not exist in the system", userId);
            throw new UserException("Login failed");
        }

        User loggedInUser = getUser(userId);
        ShoppingCart cartOfGuest;
        try{
            lock.acquireWrite();
            //remove guest from system
            guests.remove(guestInitialId);
            authenticationController.revokeAuthentication(guestInitialId);
            cartOfGuest = guestCarts.remove(guestInitialId);
            authenticationController.removeGuest(guestInitialId);
            permissionsController.removeGuest(guestInitialId);

            //add the registered user to the online users
            onlineRegisteredUsers.put(userId,loggedInUser);
            log.debug("User with id: {} logged in successfully", userId);
        } finally {
            lock.releaseWrite();
        }

        //merge the guest cart with the registered user cart
        ShoppingCart cartOfUser = getCartWithValidation(userId);
        cartOfUser.mergeGuestCartWithRegisteredCart(cartOfGuest);
        log.debug("Guest cart merged with user cart successfully");
    }

    public void logout(String userId) throws UserException {
        userId = userId.toLowerCase();

        if(!onlineRegisteredUsers.containsKey(userId)){
            log.debug("User with id: {} is not online", userId);
            throw new UserException("User with id: " + userId + " is not online");
        }

        authenticationController.revokeAuthentication(userId);

        try{
            lock.acquireWrite();
            onlineRegisteredUsers.remove(userId);
            log.debug("User with id: {} logged out successfully", userId);
        }
        finally {
            lock.releaseWrite();
        }
    }

    public void logoutAsGuest(String guestInitialId) throws UserException {
        if(!guests.containsKey(guestInitialId)){
            log.debug("Guest with id: {} is not in the market", guestInitialId);
            throw new UserException("Guest with id: " + guestInitialId + " is not in the market");
        }
        authenticationController.revokeAuthentication(guestInitialId);
        authenticationController.removeGuest(guestInitialId);
        permissionsController.removeGuest(guestInitialId);

        try{
            lock.acquireWrite();
            //the guest exits the system, therefore his cart removes from the system
            guestCarts.remove(guestInitialId);
            guests.remove(guestInitialId);
            log.debug("Guest with id: {} logged out successfully", guestInitialId);
        }
        finally {
            lock.releaseWrite();
        }
    }

    public User getUser(String userId) throws UserException {
        userId = userId.toLowerCase();

        if(guests.containsKey(userId)){
            return guests.get(userId);
        }

        Optional<RegisteredUser> user = userRepository.findById(userId);
        if(user.isPresent()){
            return user.get();
        }

        log.debug("User with id: {} does not exist", userId);
        throw new UserException("The user does not exists");
    }

    public List<Transaction> getUserTransactionHistory(String userId) throws UserException {
        userId = userId.toLowerCase();
        if(!userRepository.existsById(userId)){
            log.debug("User with id: {} does not exist", userId);
            throw new UserException("Invalid userId");
        }
        return transactionRepository.getTransactionHistoryByUser(userId);
    }
    // =============================================================================== |
    // ================================ SHOPPING CART ================================ |
    // =============================================================================== |

    public void addProductToCart(String userId, String storeId, String productId, int quantity) throws UserException, ShoppingCartException {
        userId = userId.toLowerCase();
        ShoppingCart cart = getCartWithValidation(userId);
        cart.addProduct(storeId, productId,quantity);
        log.debug("Product with id: {} added to the cart of user with id: {}", productId, userId);
    }

    public void removeProductFromCart(String userId,String storeName,String productId) throws UserException, ShoppingCartException {
        userId = userId.toLowerCase();
        ShoppingCart cart = getCartWithValidation(userId);
        cart.removeProduct(storeName,productId);
        log.debug("Product with id: {} removed from the cart of user with id: {}", productId, userId);
    }

    public void changeProductQuantity(String userId, String storeName, String productId, int quantity) throws UserException, ShoppingCartException {
        userId = userId.toLowerCase();
        ShoppingCart cart = getCartWithValidation(userId);
        cart.changeProductQuantity(storeName, productId,quantity);
        log.debug("Product with id: {} quantity changed in the cart of user with id: {}", productId, userId);
    }

    public ShoppingCart viewCart(String userId) throws UserException {
        userId = userId.toLowerCase();
        return getCartWithValidation(userId);
    }
    // =============================================================================== |
    // ================================ PURCHASE ===================================== |
    // =============================================================================== |

    public void startPurchase(String userId) throws PurchaseFailedException, UserException {
        userId = userId.toLowerCase();
        ShoppingCart cart = getCartWithValidation(userId);
        Map<String, Reservation> reservations = cart.reserveCart();
        reservations.values().forEach(reservationRepository::save);
        log.debug("Cart of user with id: {} reserved successfully", userId);
    }

    public void payForPurchase(String userId) throws PurchaseFailedException, UserException {
        userId = userId.toLowerCase();
        try{
            lock.acquireWrite();

            List<Reservation> reservations = reservationRepository.findAllById(userId);
            if(reservations.isEmpty()){
                log.debug("No reservations to pay for user with id: {}", userId);
                throw new PurchaseFailedException("No reservations to pay for");
            }

            // charge the user
            ShoppingCart cart = getCartWithValidation(userId);
            User user = getUser(userId);
            if(! paymentService.charge(user.getPaymentMethod(), cart.getTotalPrice())){
                final String finalUserId = userId;
                reservations.forEach(r -> {
                    r.cancelReservation();
                    reservationRepository.deleteReservation(finalUserId,r);
                });
                log.debug("Payment failed");
                throw new PurchaseFailedException("Payment failed");
            }

            // mark the reservations as paid
            reservations.forEach(Reservation::setPaid);
            log.debug("Mark the reservation as paid successfully");

            // document the transactions
            LocalDateTime transactionTime = LocalDateTime.now();
            for (var reservation : reservations) {
                Transaction t = reservationToTransaction(userId, reservation, transactionTime);
                transactionRepository.save(t);

                // send notifications to owners of the store
                Optional<Store> store = storeRepository.findById(reservation.storeId());
                if(store.isEmpty()){
                    throw new PurchaseFailedException("Store not found");
                }
                store.get().getOwners().forEach(ownerId -> {
                    try {
                        notificationController.sendNotification("New transactionId in your store: "+store.get().getStoreName(),
                                "Transaction id: "+t.getTransactionId(),
                                "Amazonas",
                                ownerId);
                    } catch (NotificationException e) {
                        log.error("Failed to send transactionId notification to owner with id: {} in store {}", ownerId, store.get().getStoreName());
                    }
                });

            }
            log.debug("Documented the transactions successfully");

            // give the user a new empty cart
            shoppingCartRepository.resetCart(userId);
            log.debug("The purchase completed");
        }
        finally{
            lock.releaseWrite();
        }
    }

    public boolean cancelPurchase(String userId) throws UserException {
        userId = userId.toLowerCase();
        if(!userRepository.existsById(userId)){
            log.debug("Cancel Purchase - invalid userId");
            throw new UserException("Invalid userId");
        }

        try{
            lock.acquireWrite();

            List<Reservation> reservations = reservationRepository.findAllById(userId);
            if(reservations.isEmpty()){
                log.debug("No reservations to cancel for user with id: {}", userId);
                return false;
            }

            final String finalUserId = userId;
            reservations.forEach(r -> {
                r.cancelReservation();
                reservationRepository.deleteReservation(finalUserId,r);
            });

            log.debug("The purchase canceled for user with id: {}", userId);
            return true;
        } finally {
            lock.releaseWrite();
        }
    }
    // =============================================================================== |
    // ============================= HELPER METHODS ================================== |
    // =============================================================================== |

    private Transaction reservationToTransaction(String userId, Reservation reservation, LocalDateTime transactionTime) throws PurchaseFailedException {
        String transactionId = UUID.randomUUID().toString();
        Map<Product,Integer> productToQuantity = new HashMap<>();
        for (var entry : reservation.productIdToQuantity().entrySet()) {
            Optional<Product> product = productRepository.findById(entry.getKey());
            if (product.isEmpty()) {
                throw new PurchaseFailedException("Product not found");
            }
            productToQuantity.put(product.get(), entry.getValue());
        }
        return new Transaction(transactionId,
                reservation.storeId(),
                userId,
                transactionTime,
                productToQuantity);
    }

    private ShoppingCart getCartWithValidation(String userId) throws UserException {
        userId = userId.toLowerCase();
        Optional<ShoppingCart> cart = shoppingCartRepository.findById(userId);
        if(cart.isEmpty()){
            throw new UserException("Invalid userId");
        }
        return cart.get();
    }

    private boolean isValidPassword(String password) {
        String passwordPattern = "^(?=.*[A-Z].*)(?=.*[!@#$%^&*()\\-=\\[\\]{};':\"<>?|])(?=.*[0-9].*)(?=.*[a-z].*).{8,}$";
        return password.matches(passwordPattern);
    }

    private boolean isValidEmail(String email) {
        if (email == null) {
            return false;
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$";
        Pattern emailPattern = Pattern.compile(emailRegex);
        Matcher matcher = emailPattern.matcher(email);
        return matcher.matches();
    }

    private boolean isValidBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            return false;
        }
        return birthDate.isBefore(LocalDate.now().minusYears(12));
    }

    private boolean isValidUserId(String userId) {
        return !userRepository.existsById(userId) && !userId.matches("\\s+");
    }

    // =============================================================================== |
    // ================================ GETTERS ====================================== |
    // =============================================================================== |

    public Map<String, Guest> getGuests() {
        return guests;
    }

    public Map<String, User> getOnlineRegisteredUsers() {
        return onlineRegisteredUsers;
    }

    public User getOnlineUser(String UserId){
        return onlineRegisteredUsers.get(UserId);
    }

    public Guest getGuest(String GuestInitialId) {
        return guests.get(GuestInitialId);
    }
}
