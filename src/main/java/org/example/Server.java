package org.example;

import java.util.concurrent.Future;

public interface Server {
    Future<String> request(String data);
}
