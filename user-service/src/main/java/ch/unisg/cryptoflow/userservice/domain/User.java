package ch.unisg.cryptoflow.userservice.domain;

import lombok.Getter;

public class User {

    @Getter
    private String username;
    @Getter
    private String password;
    @Getter
    private String userId;

    public User(String username, String password, String userId) {
        this.userId = userId;
        this.username = username;
        this.password = password;
    }
}
