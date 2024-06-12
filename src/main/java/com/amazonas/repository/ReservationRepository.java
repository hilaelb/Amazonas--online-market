package com.amazonas.repository;

import com.amazonas.business.stores.Store;
import com.amazonas.business.stores.reservations.Reservation;
import com.amazonas.repository.abstracts.AbstractCachingRepository;
import com.amazonas.repository.mongoCollections.StoreMongoCollection;
import com.amazonas.utils.ReadWriteLock;
import org.springframework.stereotype.Component;

import java.util.*;

@Component("reservationRepository")
public class ReservationRepository{


    private final Map<String, List<Reservation>> reservationCache;
    private final ReadWriteLock reservationLock;

    public ReservationRepository() {
        reservationCache = new HashMap<>();
        reservationLock = new ReadWriteLock();
    }

    public void saveReservation(String userId, Reservation reservation) {
        reservationLock.acquireWrite();
        try {
            reservationCache.computeIfAbsent(userId, _ -> new LinkedList<>()).add(reservation);
        } finally {
            reservationLock.releaseWrite();
        }
    }

    public List<Reservation> getReservations(String userId){
        reservationLock.acquireRead();
        try {
            return reservationCache.getOrDefault(userId, Collections.emptyList());
        } finally {
            reservationLock.releaseRead();
        }
    }

    public void removeReservation(String userId, Reservation reservation) {
        reservationLock.acquireWrite();
        try {
            reservationCache.getOrDefault(userId, Collections.emptyList()).remove(reservation);
        } finally {
            reservationLock.releaseWrite();
        }
    }

}