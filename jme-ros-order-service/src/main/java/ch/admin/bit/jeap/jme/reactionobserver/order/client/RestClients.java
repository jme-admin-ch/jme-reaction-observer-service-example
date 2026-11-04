package ch.admin.bit.jeap.jme.reactionobserver.order.client;

import ch.admin.bit.jeap.jme.reactionobserver.order.OrderServiceProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

final class RestClients {

    private RestClients() {
    }

    static RestClient create(RestClient.Builder builder, String baseUrl, OrderServiceProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
