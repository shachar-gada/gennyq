package life.genny.lauchy.streams;

import io.quarkus.runtime.StartupEvent;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import life.genny.qwandaq.Answer;
import life.genny.qwandaq.attribute.Attribute;
import life.genny.qwandaq.datatype.DataType;
import life.genny.qwandaq.entity.BaseEntity;
import life.genny.qwandaq.exception.BadDataException;
import life.genny.qwandaq.message.QDataBaseEntityMessage;
import life.genny.qwandaq.models.UserToken;
import life.genny.qwandaq.utils.BaseEntityUtils;
import life.genny.qwandaq.utils.DefUtils;
import life.genny.qwandaq.utils.KafkaUtils;
import life.genny.qwandaq.utils.QwandaUtils;
import life.genny.qwandaq.validation.Validation;
import life.genny.serviceq.Service;
import life.genny.serviceq.intf.GennyScopeInit;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;





@ApplicationScoped
public class TopologyProducer {

	static Logger log = Logger.getLogger(TopologyProducer.class);

	Jsonb jsonb = JsonbBuilder.create();

	@ConfigProperty(name = "genny.enable.blacklist", defaultValue = "true")
	Boolean enableBlacklist;

	@Inject
	GennyScopeInit scope;

	@Inject
	Service service;

	@Inject
	UserToken userToken;

	@Inject
	DefUtils defUtils;

	@Inject
	QwandaUtils qwandaUtils;

	@Inject
	BaseEntityUtils beUtils;

	void onStart(@Observes StartupEvent ev) {

		if (service.showValues()) {
			log.info("Blacklist        :" + (enableBlacklist ? "ON" : "OFF"));
		}

		log.info("Initializing ServiceQ Services");
		service.fullServiceInit();
		log.info("[*] Finished Topology Startup!");
	}

	@Produces
	public Topology buildTopology() {

		// Read the input Kafka topic into a KStream instance.
		StreamsBuilder builder = new StreamsBuilder();
		builder
				.stream("data", Consumed.with(Serdes.String(), Serdes.String()))
				.peek((k, v) -> scope.init(v))
				.peek((k, v) -> log.info("Received message: " + v))
				.filter((k, v) -> (v != null))
				.mapValues((k, v) -> tidy(v))
				.filter((k, v) -> validate(v))
				.peek((k, v) -> log.info("Forwarding valid message"))
				.to("valid_data", Produced.with(Serdes.String(), Serdes.String()));

		return builder.build();
	}

	/**
	 * Helper function to tidy some values
	 *
	 * @param data
	 * @return
	 */
	public String tidy(String data) {

		return data.replaceAll("Adamm", "Adam");
	}

	/**
	 * Function for validating a data message.
	 *
	 * @param data the data to validate
	 * @return Boolean
	 */
	public Boolean validate(String data) {

		JsonObject json = jsonb.fromJson(data, JsonObject.class);

		if(json.containsKey("empty")) {
			log.info("Detected a payload with empty=false.. ignoring & proceding..");
			return false;
		}

		JsonArray items = json.getJsonArray("items");

		for (int i = 0; i < items.size(); i++) {

			Answer answer = jsonb.fromJson(items.get(i).toString(), Answer.class);

			// TODO: check questionCode by fetching from Questions
			// TODO: check askID by fetching from Tasks

			// check that user is the source of message
			if (!(userToken.getUserCode()).equals(answer.getSourceCode())) {
				// log.errorv("UserCode {} does not match answer source {}",
				// userToken.getUserCode(), answer.getSourceCode());
				log.error("UserCode " + userToken.getUserCode() + " does not match answer source "
						+ answer.getSourceCode());
				return blacklist();
			}

			// check source entity exists
			BaseEntity sourceBe = beUtils.getBaseEntityByCode(answer.getSourceCode());
			if (sourceBe == null) {
				log.error("Source " + answer.getSourceCode() + " does not exist");
				return blacklist();
			}
			log.info("Source = " + sourceBe.getCode() + ":" + sourceBe.getName());

			// check target entity exist
			BaseEntity targetBe = beUtils.getBaseEntityByCode(answer.getTargetCode());
			if (targetBe == null) {
				log.error("Target " + answer.getTargetCode() + " does not exist");
				return blacklist();
			}

			// check DEF was found for target
			BaseEntity defBe = defUtils.getDEF(targetBe);
			if (defBe == null) {
				// log.errorv("DEF entity not found for {}", targetBe.getCode());
				log.error("DEF entity not found for " + targetBe.getCode());
				return blacklist();
			}

			// check attribute code is allowed by targetDEF
			if (!defBe.containsEntityAttribute("ATT_" + answer.getAttributeCode())) {
				// log.errorv("AttributeCode {} not allowed for {}", answer.getAttributeCode(),
				// defBe.getCode());
				log.error("AttributeCode " + answer.getAttributeCode() + " not allowed for " + defBe.getCode());
				return blacklist();
			}

			// check attribute exists
			Attribute attribute = qwandaUtils.getAttribute(answer.getAttributeCode());
			if (attribute == null) {
				// log.errorv("AttributeCode {} does not existing", answer.getAttributeCode());
				log.error("AttributeCode " + answer.getAttributeCode() + " does not existing");
				return blacklist();
			}

			DataType dataType = attribute.getDataType();

			// HACK: TODO ACC - To send back an empty LNK_PERSON for a bucket search
			if (("LNK_PERSON".equals(answer.getAttributeCode()))
					&& ("BKT_APPLICATIONS".equals(answer.getTargetCode()))
					&& ("[]".equals(answer.getValue()))) {

				// So send back a dummy empty value for the LNK_PERSON
				try {
					targetBe.setValue(attribute, "[]");

					QDataBaseEntityMessage responseMsg = new QDataBaseEntityMessage(targetBe);
					responseMsg.setTotal(1L);
					responseMsg.setReturnCount(1L);
					responseMsg.setToken(userToken.getToken());

					KafkaUtils.writeMsg("webdata", responseMsg);
					log.info("Detected cleared BKT_APPLICATIONS search from " + userToken.getEmailUserCode());

				} catch (BadDataException e) {
					e.printStackTrace();
				}
			}

			if ("PRI_ABN".equals(answer.getAttributeCode())) {

				if (!isValidABN(answer.getValue())) {
					// log.errorv("invalid ABN {}", answer.getValue());
					log.error("invalid ABN " + answer.getValue());
					return blacklist();
				}

			} else if ("PRI_CREDITCARD".equals(answer.getAttributeCode())) {

				if (!isValidCreditCard(answer.getValue())) {
					// log.errorv("invalid Credit Card {}", answer.getValue());
					log.error("invalid Credit Card " + answer.getValue());
					return blacklist();
				}

			} else {
				Boolean isAnyValid = false;

				// check the answer field and allow through if null
				if (answer.getValue() == null) {
					log.warn("Received a null answer field from: " + userToken.getUserCode() + ", for: "
							+ answer.getAttributeCode());
					isAnyValid = true;
					continue;
				}

				for (Validation validation : dataType.getValidationList()) {

					// Now check the validation
					String regex = validation.getRegex();
					log.info("Answer Value: " + answer.getValue());
					boolean regexOk = Pattern.compile(regex).matcher(answer.getValue()).matches();

					if (regexOk) {
						isAnyValid = true;
						// log.infov("Regex OK! [{}] for regex {}", answer.getValue().toString(),
						// regex);
						log.info("Regex OK! [ " + answer.getValue() + " ] for regex " + regex);
						break;
					}
					// log.errorv("Regex failed! Att: [{}] {} [{}] for regex {} ... {}",
					// answer.getAttributeCode(),
					// attribute.getDataType().getDttCode(),
					// answer.getValue(),
					// regex,
					// validation.getErrormsg());
					log.error("Regex failed! " + regex + " ... " + validation.getErrormsg());
				}

				// blacklist if none of the regex match
				if (!isAnyValid) {
					return blacklist();
				}
			}
		}

		return true;
	}

	/**
	 * Blacklist the user if blacklists are enabled, and return
	 * a Boolean representing whether or not the messsage
	 * should be considered valid.
	 *
	 * @return Boolean
	 */
	public Boolean blacklist() {

		String uuid = userToken.getUuid();

		log.info("BLACKLIST " + (enableBlacklist ? "ON" : "OFF") + " " + userToken.getEmail() + ":" + uuid);

		if (!enableBlacklist) {
			return true;
		}

		KafkaUtils.writeMsg("blacklist", uuid);
		return false;
	}

	/**
	 * Helper function for checking ABN validity.
	 *
	 * Thanks to "Joker" from stackOverflow -
	 * https://stackoverflow.com/users/3949925/joker
	 *
	 * @param abnCode The ABN to check
	 * @return {@link Boolean}: ABN is valid
	 */
	public static Boolean isValidABN(final String abnCode) {

		if (abnCode.matches("[0-9]+") && abnCode.length() != 11) {
			return false;
		}
		final int[] weights = { 10, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19 };
		// split abn number string by digits to get int array
		try {
			int[] abnDigits = Stream.of(abnCode.split("\\B")).mapToInt(Integer::parseInt).toArray();
			// reduce by applying weight[index] * abnDigits[index] (NOTE: substract 1 for
			// the first digit in abn number)
			int sum = IntStream.range(0, weights.length).reduce(0,
					(total, idx) -> total + weights[idx] * (idx == 0 ? abnDigits[idx] - 1 : abnDigits[idx]));
			return (sum % 89 == 0);
		} catch (NumberFormatException e) {
			log.error("Attempted to parse valid ABN of: " + abnCode);
			e.printStackTrace();
			return false;
		} catch (Exception e) {
			log.error("Attempted to parse valid ABN of: " + abnCode);
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Helper function to check if a credit card number is valid
	 *
	 * @param creditCardNumber Credit card number to check
	 * @return {@link Boolean}: Number is valid
	 */
	public static boolean isValidCreditCard(String creditCardNumber) {
		int sum = 0;
		boolean alternate = false;
		for (int i = creditCardNumber.length() - 1; i >= 0; i--) {
			int n = Integer.parseInt(creditCardNumber.substring(i, i + 1));
			if (alternate) {
				n *= 2;
				if (n > 9) {
					n = (n % 10) + 1;
				}
			}
			sum += n;
			alternate = !alternate;
		}
		return (sum % 10 == 0);
	}

}
