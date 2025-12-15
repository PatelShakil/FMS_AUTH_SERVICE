/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package utils;

import java.io.Serializable;
import java.util.Optional;

/**
 *
 * @author Acer
 */
public class Resource<T> implements Serializable {

    private Boolean status;
    private String message;
    private Optional<T> data;

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Optional<T> getData() {
        return data;
    }

    public void setData(Optional<T> data) {
        this.data = data;
    }

    public static <T> Resource<T> success(String message, T data) {
        Resource<T> res = new Resource<>();
        res.setStatus(true);
        res.setMessage(message);
        res.setData(Optional.ofNullable(data));
        return res;
    }

    public static <T> Resource<T> error(String message) {
        Resource<T> res = new Resource<>();
        res.setStatus(false);
        res.setMessage(message);
        res.setData(Optional.empty());
        return res;
    }

}
