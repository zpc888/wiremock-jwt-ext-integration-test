package com.example.serviceapp.bytecode;

import java.util.Map;

public interface RemoteCallInterface {
    <Req, Res> Res post(String url, Req req, Class<Res> responseType);
    <Req, Res> Res post(String url, Req req, Class<Res> responseType, Map<String, String> queryParams, Map<String, String> headers);
    <Req, Res> Res get(String url, Class<Res> responseType);
    <Req, Res> Res get(String url, Class<Res> responseType, Map<String, String> queryParams, Map<String, String> headers);
}
