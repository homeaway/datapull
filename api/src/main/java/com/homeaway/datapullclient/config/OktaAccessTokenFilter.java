package com.homeaway.datapullclient.config;

import com.okta.jwt.JoseException;
import com.okta.jwt.Jwt;
import com.okta.jwt.JwtHelper;
import com.okta.jwt.JwtVerifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.CrossOrigin;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

@CrossOrigin
@Component
public class OktaAccessTokenFilter implements Filter {

    private JwtVerifier jwtVerifier;

    @Autowired
    private DataPullClientConfig config;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

        try {
            String url = config.getOktaUrl();
            if(url == null || url.isEmpty())
                return;

            this.jwtVerifier = new JwtHelper().setIssuerUrl(url).setAudience("api://default")
                    .build();

        } catch (IOException e) {
            throw new ServletException("Failed to create JWT Verifier", e);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if(jwtVerifier == null) {
            chain.doFilter(request, response);
        }
        else{
            String authHeader = httpRequest.getHeader("Authorization");

            boolean isApiAccess = httpRequest.getRequestURI().contains("/api/v1");

            if (isApiAccess && authHeader != null
                    && !authHeader.isEmpty()
                    && authHeader.startsWith("Bearer ")) {
                String jwtString = authHeader.replaceFirst("^Bearer ", "");

                try {
                    Jwt token = jwtVerifier.decodeAccessToken(jwtString);
                    Map<String, Object> claims = token.getClaims();
                    DataPullContext context = DataPullContextHolder.getContext();
                    if(context == null) {
                        context = new DataPullContext();
                        DataPullContextHolder.setContext(context);
                    }
                    context.setAuthenticatedUser((String)claims.get("sub"));
                    chain.doFilter(request, response);
                    return;

                } catch (Exception e) {
                    httpRequest.getServletContext().log("Failed to decode Access Token", e);
                    httpResponse.setHeader("WWW-Authenticate", "Bearer realm=\"DataPull\"");
                    httpResponse.sendError(401, "Unauthorized");
                    chain.doFilter(request, response);
                }
            }
            else{
                httpResponse.setHeader("WWW-Authenticate", "Bearer realm=\"DataPull\"");
                httpResponse.sendError(401, "Unauthorized");
                chain.doFilter(request, response);
            }
        }
    }

    @Override
    public void destroy() {
        //super.destroy();
    }
}