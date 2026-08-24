package org.example;

import java.util.HashMap;

public class Context {
    private final ThreadLocal<HashMap<String, Object>> context = ThreadLocal.withInitial(HashMap::new);

    public void put(String key, Object value) {
        this.context.get().put(key, value);
    }

    public Object get(String key) {
        return this.context.get().get(key);
    }

    public void clear() {
        this.context.get().clear();
        this.context.remove();
    }
}
