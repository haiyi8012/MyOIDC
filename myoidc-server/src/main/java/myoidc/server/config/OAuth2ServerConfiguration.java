package myoidc.server.config;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import myoidc.server.Constants;
import myoidc.server.service.OauthService;
import myoidc.server.service.SecurityService;
import myoidc.server.service.oauth.CustomJdbcClientDetailsService;
import myoidc.server.service.oauth.OauthUserApprovalHandler;
import myoidc.server.service.oauth.SOSAuthorizationCodeServices;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.OAuth2RequestFactory;
import org.springframework.security.oauth2.provider.approval.UserApprovalHandler;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeServices;
import org.springframework.security.oauth2.provider.request.DefaultOAuth2RequestFactory;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyPair;

/**
 * 2018/2/8
 * <p>
 * <p>
 * OAuth2 config
 *
 * @author Shengzhao Li
 */
@Configuration
public class OAuth2ServerConfiguration implements Constants {


    // unity resource
    @Configuration
    @EnableResourceServer
    protected static class UnityResourceServerConfiguration extends ResourceServerConfigurerAdapter {

        @Override
        public void configure(ResourceServerSecurityConfigurer resources) {
            resources.resourceId(RESOURCE_ID).stateless(false);
        }

        @Override
        public void configure(HttpSecurity http) throws Exception {
            http
                    // Since we want the protected resources to be accessible in the UI as well we need
                    // session creation to be allowed (it's disabled by default in 2.0.6)
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .and()
                    .requestMatchers().antMatchers("/unity/**")
                    .and()
                    .authorizeRequests()
                    .antMatchers("/unity/**").access("#oauth2.hasScope('read') and hasRole('ROLE_UNITY')");

        }

    }

    // mobile resource
    @Configuration
    @EnableResourceServer
    protected static class MobileResourceServerConfiguration extends ResourceServerConfigurerAdapter {

        @Override
        public void configure(ResourceServerSecurityConfigurer resources) {
            resources.resourceId(RESOURCE_ID).stateless(false);
        }

        @Override
        public void configure(HttpSecurity http) throws Exception {
            http
                    // Since we want the protected resources to be accessible in the UI as well we need
                    // session creation to be allowed (it's disabled by default in 2.0.6)
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .and()
                    .requestMatchers().antMatchers("/m/**")
                    .and()
                    .authorizeRequests()
                    .antMatchers("/m/**").access("#oauth2.hasScope('read') and hasRole('ROLE_MOBILE')");

        }

    }

    @Configuration
    @EnableAuthorizationServer
    protected static class AuthorizationServerConfiguration extends AuthorizationServerConfigurerAdapter {


        @Autowired
        private TokenStore tokenStore;


        @Autowired
        private ClientDetailsService clientDetailsService;


        @Autowired
        private OauthService oauthService;


        @Autowired
        private AuthorizationCodeServices authorizationCodeServices;


        @Autowired
        private SecurityService userDetailsService;


        @Autowired
        @Qualifier("authenticationManagerBean")
        private AuthenticationManager authenticationManager;


        @Override
        public void configure(ClientDetailsServiceConfigurer clients) throws Exception {

            clients.withClientDetails(clientDetailsService);
        }


        /**
         * JwtAccessTokenConverter config
         *
         * @return JwtAccessTokenConverter
         * @throws Exception e
         * @since 1.1.0
         */
        @Bean
        public JwtAccessTokenConverter accessTokenConverter() throws Exception {
            JwtAccessTokenConverter accessTokenConverter = new JwtAccessTokenConverter();
            //加载 keystore配置文件
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(KEYSTORE_NAME)) {
                String keyJson = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));
                PublicJsonWebKey pjwk = RsaJsonWebKey.Factory.newPublicJwk(keyJson);
                accessTokenConverter.setKeyPair(new KeyPair(pjwk.getPublicKey(), pjwk.getPrivateKey()));
            }
//            System.out.println("Key:\n" + accessTokenConverter.getKey());
            return accessTokenConverter;
        }

        @Bean
        public TokenStore tokenStore(DataSource dataSource) throws Exception {
//            return new CustomJdbcTokenStore(dataSource);
            return new JwtTokenStore(accessTokenConverter());
        }

        /**
         * Redis TokenStore (有Redis场景时使用)
         */
//        @Bean
//        public TokenStore tokenStore(RedisConnectionFactory connectionFactory) {
//            final RedisTokenStore redisTokenStore = new RedisTokenStore(connectionFactory);
//            //prefix
//            redisTokenStore.setPrefix(RESOURCE_ID);
//            return redisTokenStore;
//        }
        @Bean
        public ClientDetailsService clientDetailsService(DataSource dataSource) {
            return new CustomJdbcClientDetailsService(dataSource);
        }


        @Bean
        public AuthorizationCodeServices authorizationCodeServices(DataSource dataSource) {
            return new SOSAuthorizationCodeServices(dataSource);
        }


        @Override
        public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
            endpoints.accessTokenConverter(accessTokenConverter());

            endpoints.tokenStore(tokenStore)
                    .authorizationCodeServices(authorizationCodeServices)
                    .userDetailsService(userDetailsService)
                    .userApprovalHandler(userApprovalHandler())
                    .authenticationManager(authenticationManager);
        }

        @Override
        public void configure(AuthorizationServerSecurityConfigurer oauthServer) throws Exception {
            oauthServer.realm("MyOIDC")
                    // 支持 client_credentials 的配置
                    .allowFormAuthenticationForClients();
        }

        @Bean
        public OAuth2RequestFactory oAuth2RequestFactory() {
            return new DefaultOAuth2RequestFactory(clientDetailsService);
        }


        @Bean
        public UserApprovalHandler userApprovalHandler() {
            OauthUserApprovalHandler userApprovalHandler = new OauthUserApprovalHandler();
            userApprovalHandler.setOauthService(oauthService);
            userApprovalHandler.setTokenStore(tokenStore);
            userApprovalHandler.setClientDetailsService(this.clientDetailsService);
            userApprovalHandler.setRequestFactory(oAuth2RequestFactory());
            return userApprovalHandler;
        }

    }


}
