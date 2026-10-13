package com.example.serviceapp.bytecode;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CodeAnalysisController {
    @GetMapping("/api/bytecode/resources/{resourceId}")
    public ResponseEntity<Map<String,Object>> getResource(@PathVariable String resourceId){
        Map<String,Object> map = new HashMap<>();
        map.put("resourceId",resourceId);
        map.put("composite", Map.of("key1", "value1", "key2", "value2"));
        map.put("list", List.of("1", "2"));
        return ResponseEntity.ok(map);
    }

    @PostMapping("/api/bytecode/resources")
    public ResponseEntity<Map<String,Object>> addResource(@RequestBody Map<String,Object> map){
        Map<String, Object> ret = new HashMap<>(map);
        ret.put("resourceId", UUID.randomUUID().toString());
        return ResponseEntity.ok(ret);
    }

    @RequestMapping(path = "/api/bytecode/resources/{resourceId}", method = RequestMethod.PATCH)
    public ResponseEntity<CodeAnalysisResponse> updateResource(@PathVariable String resourceId,
                                                             @RequestParam(name="override") boolean override,
                                                             @RequestHeader(name="myHeader") String myHeader,
                                                             @RequestBody CodeAnalysisRequest request){
        CodeAnalysisResponse ret = new CodeAnalysisResponse();
        return ResponseEntity.ok(ret);
    }

    public static class CodeAnalysisRequest {
    }

    public static class CodeAnalysisResponse {
    }
}
