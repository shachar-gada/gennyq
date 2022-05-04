package life.genny.qwandaq.models;

import io.quarkus.runtime.annotations.RegisterForReflection;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

@RegisterForReflection
@ApplicationScoped
public class ServiceToken extends GennyToken {

	private static final long serialVersionUID = 1L;
	static final Logger log = Logger.getLogger(ServiceToken.class);

	public ServiceToken() { }

	public ServiceToken(final String code, final String token) {
		super(code, token);
	}

	public ServiceToken(final String token) {
		super(token);
	}

    @PostConstruct
    void init() {
		log.info("CONSTRUCTING ServiceToken Bean");
    }

    @PreDestroy
    void destroy() {
		log.info("DESTROYING ServiceToken Bean");
    }

}