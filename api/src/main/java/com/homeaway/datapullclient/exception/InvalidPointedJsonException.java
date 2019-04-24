package com.homeaway.datapullclient.exception;

public class InvalidPointedJsonException extends ProcessingException {
    public InvalidPointedJsonException(){

    }

    public InvalidPointedJsonException(String message){
        super(message);
    }
}