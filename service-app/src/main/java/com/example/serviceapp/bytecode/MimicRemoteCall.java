package com.example.serviceapp.bytecode;

import java.util.Map;

public class MimicRemoteCall {
    private final RemoteCallInterface remoteCallInterface;

    public MimicRemoteCall(RemoteCallInterface remoteCallInterface) {
        this.remoteCallInterface = remoteCallInterface;
    }

    public void method1() {
        remoteCallInterface.get("/a/b/c1", Res1.class);
        remoteCallInterface.post("/x/y/z1", new Req1(), Res1.class);
    }

    public void method2() {
        remoteCallInterface.get("/a/b/c2", Res2.class, Map.of("q1", "v1"), Map.of("h1", "v1"));
    }

    public void method3() {
        remoteCallInterface.post("/x/y/z2", new Req2(), Res2.class, Map.of("q2", "v2"), Map.of("h2", "v2"));
    }

    public static class Req1 {}
    public static class Req2 {}
    public static class Res1 {}
    public static class Res2 {}
}
