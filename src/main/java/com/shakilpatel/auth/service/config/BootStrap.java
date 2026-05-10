package com.shakilpatel.auth.service.config;
import jakarta.ws.rs.ApplicationPath;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.microprofile.auth.LoginConfig;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirements;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.servers.Server;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

@LoginConfig(authMethod = "MP-JWT")
@SuppressWarnings({"EmptyClass", "SuppressionAnnotation"})
@ApplicationPath("/api")
@SecurityScheme(
        securitySchemeName = "jwt",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@OpenAPIDefinition(
        info = @Info(
                title = "BoloIndia Auth Service API",
                version = "1.0",
                description = "Authentication & User Management Service",
                contact = @Contact(
                        name = "MuhammadShakil Patel",
                        email = "shakil@boloindia.in"
                )
        ),
        servers = {
            @Server(url = "http://localhost:6001/auth-service", description = "Development")
        },
        security = {
                @SecurityRequirement(name = "jwt")
        }
)
public class BootStrap extends jakarta.ws.rs.core.Application {
    
    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(MultiPartFeature.class); // Register multipart support
        classes.add(api.AuthResource.class);
        classes.add(api.DistributorResource.class);

        // 🔥 REGISTER YOUR GLOBAL EXCEPTION MAPPERS HERE
        classes.add(utils.GlobalExceptionMapper.class);
        classes.add(utils.JwtExceptionMapper.class);
        classes.add(utils.AuthExceptionMapper.class);

        return classes;
    }
}

//Generating private and public key for rsa encryption
//mkdir -p src/main/resources/keys
//openssl genrsa -out src/main/resources/keys/privateKey.pem 2048
//openssl rsa -in src/main/resources/keys/privateKey.pem -pubout -out src/main/resources/keys/publicKey.pem
//writing RSA key
