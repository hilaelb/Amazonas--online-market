package com.amazonas.business.inventory;

import java.util.Objects;

public final class Product {
    private final String productID;
    private final String nameProduct;
    private final double price;
    private final String category;
    private final int rate;
    private final String description;
    private boolean enabled;

    public Product(
            String productID,
            String nameProduct,
            double price,
            String category,
            int rate,
            String description,
            Boolean enabled
    ) {
        this.productID = productID;
        this.nameProduct = nameProduct;
        this.price = price;
        this.category = category;
        this.rate = rate;
        this.description = description;
        this.enabled = enabled;
    }

    public String productID() {
        return productID;
    }

    public String nameProduct() {
        return nameProduct;
    }

    public double price() {
        return price;
    }

    public String category() {
        return category;
    }

    public int rate() {
        return rate;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Product) obj;
        return Objects.equals(this.productID, that.productID) &&
                Objects.equals(this.nameProduct, that.nameProduct) &&
                Double.doubleToLongBits(this.price) == Double.doubleToLongBits(that.price) &&
                Objects.equals(this.category, that.category) &&
                this.rate == that.rate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(productID, nameProduct, price, category, rate);
    }

    @Override
    public String toString() {
        return "Product[" +
                "productID=" + productID + ", " +
                "nameProduct=" + nameProduct + ", " +
                "price=" + price + ", " +
                "category=" + category + ", " +
                "rate=" + rate + ']';
    }


    public String description() {
        return description;
    }

    public void setEnabled(){
        enabled = true;
    }
    public void setDisable(){
        enabled = true;
    }
    public void setDisabled(){
        enabled = false;
    }
    public boolean getEnabled(){
        return enabled;
    }
}
