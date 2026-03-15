package ch.unisg.cryptoflow.userservice.domain;

import lombok.Getter;

public class User {

    @Getter
    private String username;
    @Getter
    private String password;

    public User(String username, String password) {
        // this.id = id;
        this.username = username;
        this.password = password;
    }

}
