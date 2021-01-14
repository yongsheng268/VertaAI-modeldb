package ai.verta.modeldb.authservice;

import ai.verta.modeldb.App;
import ai.verta.modeldb.ModelDBAuthInterceptor;
import ai.verta.modeldb.ModelDBConstants;
import ai.verta.modeldb.ModelDBMessages;
import ai.verta.modeldb.utils.ModelDBUtils;
import ai.verta.uac.AuthzServiceGrpc;
import ai.verta.uac.OrganizationServiceGrpc;
import ai.verta.uac.RoleServiceGrpc;
import ai.verta.uac.TeamServiceGrpc;
import ai.verta.uac.UACServiceGrpc;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.MetadataUtils;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuthServiceChannel implements AutoCloseable {

  private static final Logger LOGGER = LogManager.getLogger(AuthServiceChannel.class);
  private final ManagedChannel authServiceChannel;
  private RoleServiceGrpc.RoleServiceBlockingStub roleServiceBlockingStub;
  private RoleServiceGrpc.RoleServiceFutureStub roleServiceFutureStub;
  private AuthzServiceGrpc.AuthzServiceBlockingStub authzServiceBlockingStub;
  private UACServiceGrpc.UACServiceBlockingStub uacServiceBlockingStub;
  private TeamServiceGrpc.TeamServiceBlockingStub teamServiceBlockingStub;
  private OrganizationServiceGrpc.OrganizationServiceBlockingStub organizationServiceBlockingStub;
  private final String serviceUserEmail;
  private final String serviceUserDevKey;

  public AuthServiceChannel() {
    final App app = App.getInstance();
    final String host = app.getAuthServerHost();
    final Integer port = app.getAuthServerPort();
    LOGGER.trace(ModelDBMessages.HOST_PORT_INFO_STR, host, port);
    if (host != null && port != null) { // AuthService not available.
      authServiceChannel =
          ManagedChannelBuilder.forTarget(host + ModelDBConstants.STRING_COLON + port)
              .usePlaintext()
              .build();

      this.serviceUserEmail = app.getServiceUserEmail();
      this.serviceUserDevKey = app.getServiceUserDevKey();
    } else {
      final Status status =
          Status.newBuilder()
              .setCode(Code.UNAVAILABLE_VALUE)
              .setMessage("Host OR Port not found for contacting authentication service")
              .build();
      throw StatusProto.toStatusRuntimeException(status);
    }
  }

  @Override
  public void close() throws StatusRuntimeException {
    try {
      if (authServiceChannel != null) {
        authServiceChannel.shutdown();
      }
    } catch (final Exception ex) {
      LOGGER.trace(ModelDBConstants.AUTH_SERVICE_CHANNEL_CLOSE_ERROR, ex);
      final Status status =
          Status.newBuilder()
              .setCode(Code.INTERNAL_VALUE)
              .setMessage(ModelDBConstants.AUTH_SERVICE_CHANNEL_CLOSE_ERROR + ex.getMessage())
              .build();
      throw StatusProto.toStatusRuntimeException(status);
    } finally {
      if (authServiceChannel != null && !authServiceChannel.isShutdown()) {
        try {
          authServiceChannel.awaitTermination(30, TimeUnit.SECONDS);
        } catch (final InterruptedException ex) {
          LOGGER.warn(ex.getMessage(), ex);
          final Status status =
              Status.newBuilder()
                  .setCode(Code.INTERNAL_VALUE)
                  .setMessage("AuthService channel termination error: " + ex.getMessage())
                  .build();
          throw StatusProto.toStatusRuntimeException(status);
        }
      }
    }
  }

  public AuthzServiceGrpc.AuthzServiceBlockingStub getAuthzServiceBlockingStub(
      final Metadata requestHeaders) {
    if (authzServiceBlockingStub == null) {
      initAuthzServiceStubChannel(requestHeaders);
    }
    return authzServiceBlockingStub;
  }

  private Metadata getMetadataHeaders() {
    final int backgroundUtilsCount = ModelDBUtils.getRegisteredBackgroundUtilsCount();
    LOGGER.trace("Header attaching with stub : backgroundUtilsCount : {}", backgroundUtilsCount);
    final Metadata requestHeaders;
    if (backgroundUtilsCount > 0 && ModelDBAuthInterceptor.METADATA_INFO.get() == null) {
      requestHeaders = new Metadata();
      final Metadata.Key<String> email_key =
          Metadata.Key.of("email", Metadata.ASCII_STRING_MARSHALLER);
      final Metadata.Key<String> dev_key_underscore =
          Metadata.Key.of("developer_key", Metadata.ASCII_STRING_MARSHALLER);
      final Metadata.Key<String> dev_key_hyphen =
          Metadata.Key.of("developer-key", Metadata.ASCII_STRING_MARSHALLER);
      final Metadata.Key<String> source_key =
          Metadata.Key.of("source", Metadata.ASCII_STRING_MARSHALLER);

      requestHeaders.put(email_key, this.serviceUserEmail);
      requestHeaders.put(dev_key_underscore, this.serviceUserDevKey);
      requestHeaders.put(dev_key_hyphen, this.serviceUserDevKey);
      requestHeaders.put(source_key, "PythonClient");
    } else {
      requestHeaders = ModelDBAuthInterceptor.METADATA_INFO.get();
    }
    return requestHeaders;
  }

  public OrganizationServiceGrpc.OrganizationServiceBlockingStub
      getOrganizationServiceBlockingStub() {
    if (organizationServiceBlockingStub == null) {
      initOrganizationServiceStubChannel();
    }
    return organizationServiceBlockingStub;
  }

  public RoleServiceGrpc.RoleServiceBlockingStub getRoleServiceBlockingStub() {
    if (roleServiceBlockingStub == null) {
      initRoleServiceStubChannel();
    }
    return roleServiceBlockingStub;
  }

  public RoleServiceGrpc.RoleServiceFutureStub getRoleServiceFutureStub() {
    if (roleServiceFutureStub == null) {
      initRoleServiceFutureStubChannel();
    }
    return roleServiceFutureStub;
  }

  public TeamServiceGrpc.TeamServiceBlockingStub getTeamServiceBlockingStub() {
    if (teamServiceBlockingStub == null) {
      initTeamServiceStubChannel();
    }
    return teamServiceBlockingStub;
  }

  public UACServiceGrpc.UACServiceBlockingStub getUacServiceBlockingStub() {
    if (uacServiceBlockingStub == null) {
      initUACServiceStubChannel();
    }
    return uacServiceBlockingStub;
  }

  private void initAuthzServiceStubChannel(Metadata requestHeaders) {
    if (requestHeaders == null) {
      requestHeaders = getMetadataHeaders();
    }
    LOGGER.trace("Header attaching with stub : {}", requestHeaders);
    final ClientInterceptor clientInterceptor =
        MetadataUtils.newAttachHeadersInterceptor(requestHeaders);
    authzServiceBlockingStub =
        AuthzServiceGrpc.newBlockingStub(authServiceChannel).withInterceptors(clientInterceptor);
    LOGGER.trace("Header attached with stub");
  }

  private void initOrganizationServiceStubChannel() {
    final Metadata requestHeaders = getMetadataHeaders();
    LOGGER.trace("Header attaching with stub : {}", requestHeaders);
    final ClientInterceptor clientInterceptor =
        MetadataUtils.newAttachHeadersInterceptor(requestHeaders);
    organizationServiceBlockingStub =
        OrganizationServiceGrpc.newBlockingStub(authServiceChannel)
            .withInterceptors(clientInterceptor);
    LOGGER.trace("Header attached with stub");
  }

  private void initRoleServiceFutureStubChannel() {
    final Metadata requestHeaders = getMetadataHeaders();
    LOGGER.trace("Header attaching with stub : {}", requestHeaders);
    final ClientInterceptor clientInterceptor =
        MetadataUtils.newAttachHeadersInterceptor(requestHeaders);
    roleServiceFutureStub =
        RoleServiceGrpc.newFutureStub(authServiceChannel).withInterceptors(clientInterceptor);
    LOGGER.trace("Header attached with stub");
  }

  private void initRoleServiceStubChannel() {
    final Metadata requestHeaders = getMetadataHeaders();
    LOGGER.trace("Header attaching with stub : {}", requestHeaders);
    final ClientInterceptor clientInterceptor =
        MetadataUtils.newAttachHeadersInterceptor(requestHeaders);
    roleServiceBlockingStub =
        RoleServiceGrpc.newBlockingStub(authServiceChannel).withInterceptors(clientInterceptor);
    LOGGER.trace("Header attached with stub");
  }

  private void initTeamServiceStubChannel() {
    final Metadata requestHeaders = getMetadataHeaders();
    LOGGER.trace("Header attaching with stub : {}", requestHeaders);
    final ClientInterceptor clientInterceptor =
        MetadataUtils.newAttachHeadersInterceptor(requestHeaders);
    teamServiceBlockingStub =
        TeamServiceGrpc.newBlockingStub(authServiceChannel).withInterceptors(clientInterceptor);
    LOGGER.trace("Header attached with stub");
  }

  private void initUACServiceStubChannel() {
    final Metadata requestHeaders = getMetadataHeaders();
    LOGGER.trace("Header attaching with stub : {}", requestHeaders);
    final ClientInterceptor clientInterceptor =
        MetadataUtils.newAttachHeadersInterceptor(requestHeaders);
    uacServiceBlockingStub =
        UACServiceGrpc.newBlockingStub(authServiceChannel).withInterceptors(clientInterceptor);
    LOGGER.trace("Header attached with stub");
  }
}
