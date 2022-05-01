package life.genny.serviceq;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import org.jboss.logging.Logger;

import io.quarkus.runtime.annotations.RegisterForReflection;
import life.genny.qwandaq.data.GennyCache;
import life.genny.qwandaq.models.GennyToken;
import life.genny.qwandaq.models.TokenCollection;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.qwandaq.utils.CacheUtils;
import life.genny.qwandaq.utils.DatabaseUtils;
import life.genny.qwandaq.utils.DefUtils;
import life.genny.qwandaq.utils.KafkaUtils;
import life.genny.qwandaq.utils.KeycloakUtils;
import life.genny.qwandaq.utils.QwandaUtils;
import life.genny.serviceq.intf.KafkaBean;
import life.genny.serviceq.live.data.InternalProducer;

@RegisterForReflection
@ApplicationScoped
public class Service {

	static final Logger log = Logger.getLogger(Service.class);

	static Jsonb jsonb = JsonbBuilder.create();

	@ConfigProperty(name = "genny.show.values")
	Boolean showValues;

	@ConfigProperty(name = "genny.keycloak.url")
	String keycloakUrl;

	@ConfigProperty(name = "genny.keycloak.realm")
	String keycloakRealm;

	@ConfigProperty(name = "genny.service.username")
	String serviceUsername;

	@ConfigProperty(name = "genny.service.password")
	String servicePassword;

	@ConfigProperty(name = "genny.client.id")
	String clientId;

	@ConfigProperty(name = "genny.client.secret")
	String secret;

	@Inject
	EntityManager entityManager;

	@Inject
	InternalProducer producer;

	@Inject
	GennyCache cache;

	@Inject
	KafkaBean kafkaBean;

	@Inject
	DatabaseUtils databaseUtils;

	@Inject
	DefUtils defUtils;

	@Inject
	QwandaUtils qwandaUtils;

	@Inject
	TokenCollection tokens;

	private Boolean initialised = false;

	/**
	 * Initialize the serviceToken for our TokenCollection.
	 */
	public void initToken() {

		// fetch token and init entity utility
		GennyToken serviceToken = KeycloakUtils.getToken(keycloakUrl, keycloakRealm, clientId, secret, serviceUsername, servicePassword);

		if (serviceToken == null) {
			log.error("Service token is null for realm!: " + keycloakRealm);
		}
		log.info("ServiceToken: " + (serviceToken != null ? serviceToken.getToken() : " null"));

		// add list of allowed products
		String allowedProducts = System.getenv("PRODUCT_CODES");
		if (allowedProducts != null) {
			serviceToken.setAllowedProducts(allowedProducts.split(":"));
		}

		// update the serviceToken in our token collection
		tokens.setServiceToken(serviceToken);

		// set gennyToken as serviceToken just for initialisation purposes
		tokens.setGennyToken(serviceToken);
	}

	/**
	 * Initialize the database connection
	 */
	public void initDatabase() {
		databaseUtils.init(entityManager);
	}

	/**
	 * Initialize the cache connection
	 */
	public void initCache() {
		CacheUtils.init(cache);
	}

	/**
	 * Initialize the Kafka channels.
	 */
	public void initKafka() {
		KafkaUtils.init(kafkaBean);
	}

	/**
	 * Initialize the Attribute cache for each allowed productCode.
	 */
	public void initAttributes() {

		for (String productCode : tokens.getServiceToken().getAllowedProducts()) {
			qwandaUtils.loadAllAttributesIntoCache(productCode);
		}
	}

	/**
	 * Initialize BaseEntity Definitions for each allowed productCode.
	 */
	public void initDefinitions() {

		for (String productCode : tokens.getServiceToken().getAllowedProducts()) {
			defUtils.initializeDefs(productCode);
		}
	}

	/**
	 * log the service confiduration details.
	 */
	public void showConfiguration() {

		if (showValues) {
			log.info("service username  : " + serviceUsername);
			log.info("service password  : " + servicePassword);
			log.info("keycloakUrl       : " + keycloakUrl);
			log.info("keycloak clientId : " + clientId);
			log.info("keycloak secret   : " + secret);
			log.info("keycloak realm    : " + keycloakRealm);
		}
	}

	/**
	 * Perform a full initialization of the service.
	 */
	public void fullServiceInit() {

		if (initialised) {
			log.warn("Attempted initialisation again. Are you calling this method in more than one place?");
			return;
		}

		// log our service config
		showConfiguration();

		// init all
		initToken();
		initDatabase();
		initCache();
		initKafka();
		initAttributes();
		initDefinitions();

		initialised = true;

		log.info("[@] Service Initialised!");
	}

	/**
	 * Boolean representing whether Service should print config values.
	 *
	 * @return should show values
	 */
	public Boolean showValues() {
		return showValues;
	}
}