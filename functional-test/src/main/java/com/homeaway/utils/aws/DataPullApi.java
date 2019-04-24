package com.homeaway.utils.aws;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeaway.constants.Environment;
import com.homeaway.dto.InputJson;
import com.okta.jwt.AccessTokenVerifier;
import com.okta.jwt.JwtVerificationException;
import com.okta.jwt.JwtVerifiers;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;

import static io.restassured.RestAssured.given;
@Slf4j
public class DataPullApi {
    String responseBody;

    public int triggerDataPull(File inputJson) {
        Response response = given(getRequestSpec()).body(inputJson).post();
        responseBody = response.asString();
        return response.getStatusCode();
    }

    public int triggerDataPull(InputJson inputJson) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String node = null;
        try {
            node = mapper.writeValueAsString(inputJson);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return 500;
        }
        log.info("Input JSON Payload is {} ", node);
        Response response = given(getRequestSpec()).body(node).post();
        responseBody = response.asString();
        log.info("Status Code returned is {} ", response.getStatusCode());
        log.info("Response body {}", responseBody);
        return response.getStatusCode();
    }

    private RequestSpecification getRequestSpec() {
        RequestSpecBuilder requestSpec = new RequestSpecBuilder();
        String uri = "";
        String version = Environment.version.trim().toLowerCase();
        switch (version) {
            case "ha-internal":
                uri = Environment.dataPullUri;
                break;
            case "open-source":
                uri = Environment.dataPullOpenSourceUri;
                break;
            default:
                log.error("Version can be either internal or Open-Source");
                break;
        }
        log.info("{} Data Pull URI - {}", version.toUpperCase(), uri);
        //generateToken();
        requestSpec.
                setContentType("application/json").
                addHeader("Accept", "application/json").
                //addHeader("Authorization", "Bearer " + System.getProperty("jwt_access_token")).
                setBaseUri(uri).build();
        return requestSpec.build();

    }

    private void generateToken() {
        if (!isTokenValid()) {
            Response response = given().header("Content-Type", "application/x-www-form-urlencoded").
                    formParam("client_id", Environment.properties.getProperty("okta.client_id")).
                    formParam("client_secret", Environment.properties.getProperty("okta.client_secret")).
                    formParam("grant_type", "client_credentials").
                    formParam("scope", "custom_scope").
                    post(Environment.properties.getProperty("okta_url") + "/v1/token");
            JsonNode tokenNode = null;
            try {
                tokenNode = new ObjectMapper().readTree(response.body().prettyPrint());
                System.setProperty("jwt_access_token", tokenNode.get("access_token").asText());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private boolean isTokenValid() {
        AccessTokenVerifier jwtVerifier = JwtVerifiers.accessTokenVerifierBuilder()
                .setIssuer(Environment.properties.getProperty("okta_url"))
                .setAudience("api://default")      // defaults to 'api://default'
                .build();
        try {
            jwtVerifier.decode(System.getProperty("jwt_access_token"));
        } catch (JwtVerificationException e) {
            return false;
        }
        return true;
    }
}
