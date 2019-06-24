package eu.arrowhead.common.token;

import java.security.PrivateKey;
import java.security.PublicKey;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.ReservedClaimNames;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.springframework.util.Assert;

import eu.arrowhead.common.CommonConstants;
import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.exception.AuthException;
import eu.arrowhead.common.exception.InvalidParameterException;

public class TokenUtilities {
	
	//=================================================================================================
	// members

	private static final Logger logger = LogManager.getLogger(TokenUtilities.class);
	
	private static final AlgorithmConstraints JWS_ALG_CONSTRAINTS = new AlgorithmConstraints(ConstraintType.WHITELIST, CommonConstants.JWS_SIGN_ALG);
	private static final AlgorithmConstraints JWE_ALG_CONSTRAINTS = new AlgorithmConstraints(ConstraintType.WHITELIST, CommonConstants.JWE_KEY_MANAGEMENT_ALG);
	private static final AlgorithmConstraints JWE_ENCRYPTION_CONSTRAINTS = new AlgorithmConstraints(ConstraintType.WHITELIST, CommonConstants.JWE_ENCRYPTION_ALG);

	//=================================================================================================
	// methods
	
	//-------------------------------------------------------------------------------------------------
	public TokenInfo validateTokenAndExtractTokenInfo(final String token, final PublicKey authorizationPublicKey, final PrivateKey privateKey) {
		logger.debug("validateTokenAndExtractTokenInfo started...");
		Assert.notNull(authorizationPublicKey, "Authorization public key is null.");
		Assert.notNull(privateKey, "Caller's private key is null.");
		
		if (Utilities.isEmpty(token)) {
			throw new InvalidParameterException("No token is provided.");
		}
		
		final JwtConsumer jwtConsumer = new JwtConsumerBuilder().setRequireJwtId()
																.setRequireNotBefore()
																.setEnableRequireEncryption()
																.setEnableRequireIntegrity()
																.setExpectedIssuer(CommonConstants.CORE_SYSTEM_AUTHORIZATION)
																.setDecryptionKey(privateKey)
																.setVerificationKey(authorizationPublicKey)
																.setJwsAlgorithmConstraints(JWS_ALG_CONSTRAINTS)
																.setJweAlgorithmConstraints(JWE_ALG_CONSTRAINTS)
																.setJweContentEncryptionAlgorithmConstraints(JWE_ENCRYPTION_CONSTRAINTS)
																.build();

		try {
			final JwtClaims claims = jwtConsumer.processToClaims(token);
			final String consumerName = extractConsumerName(claims);
			final String service = extractService(claims);
			final Long expirationTime = extractExpirationTime(claims);
			
			return new TokenInfo(consumerName, service, expirationTime);
		} catch (final InvalidJwtException ex) {
			logger.debug("Token processing is failed: {}", ex.getMessage());
			logger.debug(ex);
			throw new AuthException("Token processing is failed", ex);
		} 
	}

	//=================================================================================================
	// assistant methods
	

	//-------------------------------------------------------------------------------------------------
	private TokenUtilities() {
		throw new UnsupportedOperationException();
	}
	
	//-------------------------------------------------------------------------------------------------
	private String extractConsumerName(final JwtClaims claims) throws InvalidJwtException {
		if (!claims.hasClaim(CommonConstants.JWT_CLAIM_CONSUMER_ID)) {
			throw new InvalidJwtException("Missing consumer information.", null, null, null);
		}
		
		try {
			final String consumerId = claims.getStringClaimValue(CommonConstants.JWT_CLAIM_CONSUMER_ID);
			final String[] parts = consumerId.split("\\.");

			return parts[0];
		} catch (final MalformedClaimException ex) {
			throw new InvalidJwtException("Invalid consumer information.", null, ex, null);
		}
	}
	
	//-------------------------------------------------------------------------------------------------
	private String extractService(final JwtClaims claims) throws InvalidJwtException {
		if (!claims.hasClaim(CommonConstants.JWT_CLAIM_SERVICE_ID)) {
			throw new InvalidJwtException("Missing service information.", null, null, null);
		}
		
		try {
			return claims.getStringClaimValue(CommonConstants.JWT_CLAIM_SERVICE_ID);
		} catch (final MalformedClaimException ex) {
			throw new InvalidJwtException("Invalid service information.", null, ex, null);
		}
	}

	//-------------------------------------------------------------------------------------------------
	private Long extractExpirationTime(final JwtClaims claims) throws InvalidJwtException {
		try {
			return claims.hasClaim(ReservedClaimNames.EXPIRATION_TIME) ? claims.getExpirationTime().getValueInMillis() : null;
		} catch (MalformedClaimException ex) {
			throw new InvalidJwtException("Invalid expiration time.", null, ex, null);
		}
	}
	
	//=================================================================================================
	// nested classes
	
	//-------------------------------------------------------------------------------------------------
	public static class TokenInfo {
		
		//=================================================================================================
		// members
		
		private final String consumerName;
		private final String service;
		private final Long endOfValidity;
		
		//=================================================================================================
		// methods
		
		//-------------------------------------------------------------------------------------------------
		public TokenInfo(final String consumerName, final String service, final Long endOfValidity) {
			this.consumerName = consumerName;
			this.service = service;
			this.endOfValidity = endOfValidity;
		}

		//-------------------------------------------------------------------------------------------------
		public String getConsumerName() { return consumerName; }
		public String getService() { return service; 	}
		public Long getEndOfValidity() { return endOfValidity; }
		public boolean hasEndOfValidity() { return endOfValidity != null && endOfValidity.longValue() > 0; }
	}
}