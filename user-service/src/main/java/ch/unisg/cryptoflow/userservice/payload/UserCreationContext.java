package ch.unisg.cryptoflow.userservice.payload;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class UserCreationContext {
    @Getter
    private String userName;
    @Getter
    private String password;

    public static UserCreationContext fromMap(Map<String, Object> values) {
        UserCreationContext context = new UserCreationContext();
        context.userName = (String) values.get("userName");
        context.password = (String) values.get("password");
        return context;
    }

    public Map<String, String> asMap() {
        HashMap<String, String> map = new HashMap<>();
        map.put("userName", userName);
        map.put("password", password);
        return map;
    }

    @Override
    public String toString() {
        return "UserCreationContext [userName=" + userName + ", password=" + password +"]";
    }
}
