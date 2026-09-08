package kr.sottaejap.server.notification.service;

import kr.sottaejap.server.notification.WebPushProperties;
import kr.sottaejap.server.notification.domain.PushSubscription;
import kr.sottaejap.server.notification.repository.PushSubscriptionRepository;
import nl.martijndwars.webpush.AbstractPushService;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.HttpRequest;
import nl.martijndwars.webpush.Notification;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 브라우저 Push 발송. 앱을 열지 않아도 알림이 도착하게 하는 유일한 경로다.
 *
 * <p>암호화(AES128GCM)와 VAPID 서명만 `web-push` 라이브러리에 맡기고, 전송은 {@code AiClient}와 같은
 * JDK HttpClient로 한다 — 라이브러리가 딸고 오는 HTTP 스택은 build.gradle.kts에서 제외했다.
 *
 * <p>발송은 절대 예외를 밖으로 내지 않는다. 푸시가 실패해도 인앱 알림은 이미 저장돼 있어야 하고,
 * 목록 조회가 그것 때문에 500이 되면 안 된다.
 */
@Component
public class WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    static {
        // 라이브러리는 키를 읽을 때 provider 이름 "BC"를 지정한다. 이 등록을 하는 것은 라이브러리의
        // PushService 뿐인데 우리는 그 클래스를 쓰지 않는다 — 여기서 등록하지 않으면 기동 시점에
        // NoSuchProviderException이 난다. 이미 있으면 addProvider가 -1을 돌려주고 아무 일도 하지 않는다.
        Security.addProvider(new BouncyCastleProvider());
    }

    /** 푸시 서비스가 구독을 폐기했다는 뜻. 다시 보내도 영영 실패하므로 행을 지운다. */
    private static final int GONE = 410;
    private static final int NOT_FOUND = 404;

    /** 사용자가 하루 안에 앱을 열면 인앱 목록으로 보게 된다. 그보다 오래 붙들 이유가 없다. */
    private static final int TTL_SECONDS = 86400;

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** VAPID 키가 없으면 null이다 — 그 경우 조용히 아무것도 보내지 않는다. */
    private final VapidRequests vapidRequests;

    public WebPushSender(WebPushProperties properties,
                         PushSubscriptionRepository pushSubscriptionRepository,
                         ObjectMapper objectMapper) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.vapidRequests = createRequests(properties);
    }

    public boolean isEnabled() {
        return vapidRequests != null;
    }

    /** 한 사용자의 모든 기기에 같은 알림을 보낸다. 구독이 없거나 Push가 꺼져 있으면 아무 일도 하지 않는다. */
    @Transactional
    public void send(long userId, String body, Long refId) {
        if (vapidRequests == null) {
            return;
        }
        List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUserId(userId);
        if (subscriptions.isEmpty()) {
            return;
        }
        String payload = objectMapper.writeValueAsString(Map.of(
                "title", "소때잡",
                "body", body,
                // 클라이언트 service worker가 알림을 눌렀을 때 열 경로다.
                "url", refId == null ? "/notifications" : "/notifications?ref=" + refId));
        for (PushSubscription subscription : subscriptions) {
            send(subscription, payload);
        }
    }

    /** 암호화·서명까지 끝난 요청. 실제 전송 없이 검증할 수 있도록 분리해 두었다. */
    HttpRequest buildRequest(PushSubscription subscription, String payload) throws Exception {
        return vapidRequests.build(Notification.builder()
                .endpoint(subscription.getEndpoint())
                .userPublicKey(subscription.getP256dh())
                .userAuth(subscription.getAuth())
                .payload(payload)
                .ttl(TTL_SECONDS)
                .build());
    }

    private void send(PushSubscription subscription, String payload) {
        try {
            HttpRequest request = buildRequest(subscription, payload);

            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(request.getUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(request.getBody()));
            request.getHeaders().forEach(builder::header);

            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == GONE || response.statusCode() == NOT_FOUND) {
                pushSubscriptionRepository.delete(subscription);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception failed) {
            // 인앱 알림은 이미 저장됐다. 푸시 실패로 목록 조회를 깨뜨리지 않는다.
            log.warn("Web Push 발송 실패 — subscriptionId={}", subscription.getId(), failed);
        }
    }

    private static VapidRequests createRequests(WebPushProperties properties) {
        if (!properties.isConfigured()) {
            log.info("VAPID 키가 없어 Web Push를 끕니다. 인앱 알림은 그대로 동작합니다.");
            return null;
        }
        try {
            return new VapidRequests(properties.publicKey(), properties.privateKey(), properties.subject());
        } catch (GeneralSecurityException | IllegalArgumentException invalidKey) {
            // BouncyCastle은 깨진 키 바이트에 IllegalArgumentException("Invalid point encoding")을 던진다.
            // 조용히 꺼두면 "왜 푸시가 안 오지"로 몇 시간을 쓴다. 설정이 틀렸으면 기동을 멈춘다.
            throw new IllegalStateException("VAPID 키 형식이 잘못됐습니다. README의 키 생성 절차를 보십시오.",
                    invalidKey);
        }
    }

    /**
     * `prepareRequest`가 protected라 상속으로 연다. 이 클래스가 라이브러리에서 쓰는 전부이고,
     * 라이브러리의 `PushService`(Apache HttpClient 의존)는 로드하지 않는다.
     */
    private static final class VapidRequests extends AbstractPushService<VapidRequests> {

        private VapidRequests(String publicKey, String privateKey, String subject) throws GeneralSecurityException {
            super(publicKey, privateKey, subject);
        }

        private HttpRequest build(Notification notification) throws Exception {
            return prepareRequest(notification, Encoding.AES128GCM);
        }
    }
}
