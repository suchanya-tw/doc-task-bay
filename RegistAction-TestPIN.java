package bay.epayment.authenimprove.action;


import bay.epayment.authenimprove.exeption.AuthenFailedException;
import bay.epayment.authenimprove.exeption.FlowFailedException;
import bay.epayment.authenimprove.model.KOLLoginResponse;
import bay.epayment.authenimprove.model.PushNotiModel;
import bay.epayment.authenimprove.model.PushNotiModelResponse;
import bay.epayment.authenimprove.model.debit.DirectDebitAuthenUserEntity;
import bay.epayment.authenimprove.model.debit.DirectDebitLoginResponse;
import bay.epayment.authenimprove.model.entity.EpayAuthLogAccessApi;
import bay.epayment.authenimprove.model.entity.EpayAuthLogRequest;
import bay.epayment.authenimprove.model.entity.EpayAuthProfile;
import bay.epayment.authenimprove.model.entity.EpayMobileCountryCode;
import bay.epayment.authenimprove.model.kma.AccountList;
import bay.epayment.authenimprove.model.kma.AuthenImproveRequest;
import bay.epayment.authenimprove.service.AuthenImproveService;
import bay.epayment.common.ConstantsPayment;
import bay.epayment.common.ConstantsTransaction;
import bay.epayment.common.exception.PopulateDataException;
import bay.epayment.common.exception.UpdateDataException;
import bay.epayment.payment.struts.AbstractCommonPaymentAction;
import bay.epayment.schema.transaction.Transaction;
import bay.epayment.service.OtpService;
import bay.epayment.utility.WebUtil;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.krungsri.authenimprove.AuthenImproveConstant;
import com.krungsri.authenimprove.kbolws.WSAuthen;
import com.krungsri.kolws.KOLWSConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import ws.improveauthen.kbol.AccountEntity;
import ws.improveauthen.kbol.AuthenUserEntity;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class RegistAction extends AbstractCommonPaymentAction {

	private static final Logger	logger	= LoggerFactory.getLogger(RegistAction.class);
	public ActionForward tAndC(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		String forwardName = "success";
		String userType = request.getParameter("registUserType");
		HttpSession session = request.getSession();
		session.setAttribute(AuthenImproveConstant.SESSION_KEY.USER_TYPE, userType);
		Transaction transaction = WebUtil.getTransaction(session);
		if (null == transaction) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", "transaction expired");
		}
		AuthenImproveService improveService = new AuthenImproveService();
		try {
			improveService.nextRegisterFlow(session, AuthenImproveConstant.REGISTER_STAGE.TC);
		} catch (FlowFailedException e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		}

		return forward(forwardName, mapping);
	}

	public ActionForward initRegist(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		String forwardName = "success";
		logger.info("initRegist");
		HttpSession session = request.getSession();
		String userType = (String) session.getAttribute(AuthenImproveConstant.SESSION_KEY.USER_TYPE);
		request.setAttribute("registUserType", userType);
		logger.info("registUserType :"+ userType );
		AuthenImproveService improveService = new AuthenImproveService();
		try{

			improveService.nextRegisterFlow(session, AuthenImproveConstant.REGISTER_STAGE.LOGIN);

			String ipAddress = request.getRemoteAddr();

			Map<String, Object> map = new HashMap<String, Object>();

			String refKey = improveService.generatedRefKey();
			map.put(AuthenImproveConstant.SESSION_KEY.REF_KEY, refKey);
			logger.info("refKey {}", refKey);

			try {
				logger.info("inserting log");
				EpayAuthLogRequest epayAuthLogRequest = new EpayAuthLogRequest();
				epayAuthLogRequest.setRequestType(improveService.translatorRequestType(userType));
				epayAuthLogRequest.setChannel(improveService.translatorChannel(userType));
				epayAuthLogRequest.setIpAddress(ipAddress);
				epayAuthLogRequest.setRequestStatus(AuthenImproveConstant.REQUEST_STATUS.NEW);
				epayAuthLogRequest.setRefKey(refKey);

				Calendar calendar = Calendar.getInstance();
				calendar.add(Calendar.MINUTE, 3);
				Timestamp timestamp = new Timestamp(calendar.getTimeInMillis());
				epayAuthLogRequest.setRefKeyExpired(timestamp);


				improveService.insertEPayAuthLogRequest(epayAuthLogRequest);
			} catch (Exception e) {
				logger.info("inserting log error");
				e.printStackTrace();
			}

			session.setAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE, map);
		} catch (FlowFailedException e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		}
		return forward(forwardName, mapping);
	}
	public ActionForward kmaRegist(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		logger.info("kmaRegist");
		String forwardName = "web";
		HttpSession session = request.getSession();
		String userType = (String) session.getAttribute(AuthenImproveConstant.SESSION_KEY.USER_TYPE);
		String userAgent = request.getHeader("User-Agent");
		if(userAgent.contains("Mobi")) {
			forwardName = "mobile";
		}

		AuthenImproveService improveService = new AuthenImproveService();
		try{
			improveService.nextRegisterFlow(session, AuthenImproveConstant.REGISTER_STAGE.LOGIN);

			String ipAddress = request.getRemoteAddr();

			Locale lang = (Locale) session.getAttribute("org.apache.struts.action.LOCALE");
			logger.info("lang {}", lang);
			String langCode = "TH";
			if(lang.equals(Locale.US)){
				langCode = "EN";
			}

			List<EpayMobileCountryCode> countryCodeList = improveService.getMobileCountryCodeByLanguageCode(langCode);
			logger.info("countryCodeList size {}", countryCodeList.size());
			Map<String, Object> map = new HashMap<String, Object>();

			String refKey = improveService.generatedRefKey();
			map.put(AuthenImproveConstant.SESSION_KEY.REF_KEY, refKey);
			logger.info("refKey {}", refKey);

			map.put(AuthenImproveConstant.SESSION_KEY.COUNTRY_CODE, countryCodeList);

			try {
				logger.info("inserting log");
				EpayAuthLogRequest epayAuthLogRequest = new EpayAuthLogRequest();
				epayAuthLogRequest.setRequestType(improveService.translatorRequestType(userType));
				epayAuthLogRequest.setChannel(improveService.translatorChannel(userType));
				epayAuthLogRequest.setIpAddress(ipAddress);
				epayAuthLogRequest.setRequestStatus(AuthenImproveConstant.REQUEST_STATUS.NEW);
				epayAuthLogRequest.setRefKey(refKey);

				Calendar calendar = Calendar.getInstance();
				calendar.add(Calendar.MINUTE, 3);
				map.put(AuthenImproveConstant.SESSION_KEY.REF_KEY_EXPIRED, 3);
				Timestamp timestamp = new Timestamp(calendar.getTimeInMillis());
				epayAuthLogRequest.setRefKeyExpired(timestamp);

				improveService.insertEPayAuthLogRequest(epayAuthLogRequest);
			} catch (Exception e) {
				logger.info("inserting log error");
				e.printStackTrace();
			}

			session.setAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE, map);
		} catch (FlowFailedException e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		}

		logger.info("registUserType :"+ userType );
		return forward(forwardName, mapping);
	}

	public ActionForward pushNoti(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		logger.info("pushNoti");

		String xRefKey = "";
		String xStatus = "";
		String xMessage = "";
		try {
			HttpSession session = request.getSession();
			Map<String, Object> map = (Map<String, Object>) session.getAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE);

			String refKey = (String) map.get(AuthenImproveConstant.SESSION_KEY.REF_KEY);
			xRefKey = refKey;

			PushNotiModel model = new PushNotiModel();
			model.setMobileNo(request.getParameter("mobileNo"));;
			model.setCountryCode(request.getParameter("countryCode"));
			model.setRefId(refKey);
			model.setAuthType(AuthenImproveConstant.AUTH_TYPE.REGISTER);

			logger.info("pushNoti "+ model.getMobileNo());

			AuthenImproveService improceService = new AuthenImproveService();
			PushNotiModelResponse pushNotiModelResponse = improceService.pushNotificationToKMA(model);

			xStatus = "00";
			xMessage = "OK";

		} catch (Exception ex) {
			ex.printStackTrace();
			xStatus = "99";
			xMessage = ex.getMessage();
		}

		StringBuilder sb =new StringBuilder();
		sb.append("  {  ");
		sb.append("      \"refKey\" : \""+xRefKey+"\",  ");
		sb.append("      \"status\" : \""+xStatus+"\",  ");
		sb.append("      \"message\": \""+xMessage+"\"  ");
		sb.append("  }  ");

		response.setContentType("application/json");
		try {
			OutputStream os = response.getOutputStream();
			os.write(sb.toString().getBytes());
			os.flush();
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
		}


		return null;
	}

	public ActionForward kmasetpin(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		String forwardName = "success";
		logger.info("kmasetpin");
		HttpSession session = request.getSession();
		AuthenImproveService improceService = new AuthenImproveService();
		String userType = (String) session.getAttribute(AuthenImproveConstant.SESSION_KEY.USER_TYPE);
		logger.info("userType : {}", userType);

		try {

			Map<String, Object> map = (Map<String, Object>) session.getAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE);

			String refKey = (String) map.get(AuthenImproveConstant.SESSION_KEY.REF_KEY);
			if (null == map.get(AuthenImproveConstant.SESSION_KEY.KMA_BODY)){
				request.setAttribute("MessageCode", "95");
				throw new Exception("No KMA Body");
			}
			AuthenImproveRequest kmaBody = (AuthenImproveRequest) map.get(AuthenImproveConstant.SESSION_KEY.KMA_BODY);
			String username = kmaBody.getData().getMobileNo();
			logger.info("refKey {}", refKey);

			logger.info("transformAuthenProfile");
			EpayAuthProfile epayAuthProfile = transformKMAProfile(kmaBody, userType, username);

			logger.info("check already exist");
			EpayAuthProfile isExistProfile = improceService.getEpayAuthProfileByMobileNoAndCitizenId(epayAuthProfile.getMobileNo(), epayAuthProfile.getCitizenId());
			if(null != isExistProfile) {
				request.setAttribute("MessageCode", "98");
				throw new Exception("Already regist");
			} else {
				logger.info("insertEpayAuthProfile");


				String account = kmaBody.getData().getAccountList().get(0).getAccoountNo();
				logger.info("account {}", account);

				map.put(AuthenImproveConstant.SESSION_KEY.USER_NAME, username);
				map.put(AuthenImproveConstant.SESSION_KEY.MOBILE, kmaBody.getData().getMobileNo());
				map.put(AuthenImproveConstant.SESSION_KEY.ACCOUNT, account);
				map.put(AuthenImproveConstant.SESSION_KEY.USER_TYPE, userType);
				logger.info("setProfileId");
				map.put(AuthenImproveConstant.SESSION_KEY.EPAY_DTO, epayAuthProfile);

				map.put(AuthenImproveConstant.SESSION_KEY.REF_KEY, refKey);

				session.setAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE, map);
				logger.info("username :"+ username );
				improceService.nextRegisterFlow(session, AuthenImproveConstant.REGISTER_STAGE.SETPIN);
			}


		} catch (FlowFailedException e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		} catch (UpdateDataException e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		}

		return forward(forwardName, mapping);
	}

	public ActionForward debitsetpin(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		String forwardName = "success";
		logger.info("debitsetpin");
		String cardnumber = request.getParameter("cardnumber");
		String cardpin = request.getParameter("cardpin");
		String carddob = request.getParameter("carddob");
		String expiredate = request.getParameter("expiredate");
		String cvv = request.getParameter("cvv");
		String cardidcardno = request.getParameter("cardidcardno");
		String userType = request.getParameter("userType");
		logger.info("cardnumber : {} cardpin : {} carddob : {} cardidcardno : {} userType : {}", cardnumber, cardpin, carddob, cardidcardno, userType);
		AuthenImproveService improceService = new AuthenImproveService();


		try {
			HttpSession session = request.getSession();

			if (
					StringUtils.isEmpty(cardnumber)
							|| StringUtils.isEmpty(carddob)
							|| StringUtils.isEmpty(expiredate)
							|| StringUtils.isEmpty(cardidcardno)
			) {
				request.setAttribute("MessageCode", "98");
				throw new AuthenFailedException("Authen failed");
			}


			Timestamp accessAuthenTime = new Timestamp(System.currentTimeMillis());
			DirectDebitLoginResponse
					directDebitLoginResponse = improceService
					.debitAuthenUser(cardnumber, cvv, carddob, expiredate, cardidcardno, userType, request.getRemoteAddr());
			Timestamp completeAuthenTime = new Timestamp(System.currentTimeMillis());
			logger.info(directDebitLoginResponse.toString());

			Map<String, Object> map = (Map<String, Object>) session.getAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE);

			String refKey = (String) map.get(AuthenImproveConstant.SESSION_KEY.REF_KEY);
			logger.info("refKey {}", refKey);

			//logging
			EpayAuthLogRequest epayAuthLogRequest = improceService.getEPayAuthLogRequestByRefKey(refKey);
			if(null != epayAuthLogRequest) {
				String authenRequest = "{\"cardnumber\": "+cardnumber+", \"carddob\": "+carddob+"}";

				epayAuthLogRequest.setInputParam(authenRequest);

				EpayAuthLogAccessApi epayAuthLogAccessApi = new EpayAuthLogAccessApi();
				epayAuthLogAccessApi.setRequestId(epayAuthLogRequest.getRequestId());
				epayAuthLogAccessApi.setApiName(AuthenImproveConstant.API_NAME.AUTHEN_KBOL);
				epayAuthLogAccessApi.setInputParam(authenRequest);
				epayAuthLogAccessApi.setAccessDate(accessAuthenTime);
				epayAuthLogAccessApi.setCompleteDate(completeAuthenTime);
				epayAuthLogAccessApi.setReturnCode("00");
				if(!directDebitLoginResponse.getAuthen()) {
					String textFailed = "Authen failed";
					epayAuthLogAccessApi.setReturnCode("99");
					epayAuthLogAccessApi.setReturnException(textFailed);
					epayAuthLogRequest.setRequestStatus(AuthenImproveConstant.REQUEST_STATUS.FAIL);
					epayAuthLogRequest.setRemark(textFailed);
				} else {
					ObjectMapper mapper = new ObjectMapper();
					String responseAuthen = mapper.writeValueAsString(directDebitLoginResponse);
					epayAuthLogAccessApi.setReturnParam(responseAuthen);
				}

				//insert log
				try{
					logger.info("inserting log");
					improceService.insertEPayLogAccessApi(epayAuthLogAccessApi);
					improceService.updateEPayAuthLogRequest(epayAuthLogRequest);
				} catch (Exception e) {
					logger.info("inserting log error");
					e.printStackTrace();
				}
			}

			if(directDebitLoginResponse.getAuthen()){
				logger.info("transformAuthenProfile");
				EpayAuthProfile epayAuthProfile = transformAuthenProfile(directDebitLoginResponse, userType, cardnumber);

				logger.info("check already exist");
				EpayAuthProfile isExistProfile = improceService.getEpayAuthProfileByMobileNoAndCitizenId(epayAuthProfile.getMobileNo(), epayAuthProfile.getCitizenId());
				if(null != isExistProfile) {

					request.setAttribute("MessageCode", "99");

					throw new Exception("Already regist");
				} else {

					if(!directDebitLoginResponse.getPermit()) {
						request.setAttribute("MessageCode", "96");
						throw new AuthenFailedException("No permission");
					}

					logger.info("insertEpayAuthProfile");
					map.put(AuthenImproveConstant.SESSION_KEY.USER_NAME, cardnumber);
					map.put(AuthenImproveConstant.SESSION_KEY.MOBILE, directDebitLoginResponse.getMobileNumber());
					map.put(AuthenImproveConstant.SESSION_KEY.ACCOUNT, directDebitLoginResponse.getMainAccount().getAccountNo());
					map.put(AuthenImproveConstant.SESSION_KEY.USER_TYPE, userType);
					logger.info("setProfileId");
					map.put(AuthenImproveConstant.SESSION_KEY.EPAY_DTO, epayAuthProfile);

					map.put(AuthenImproveConstant.SESSION_KEY.REF_KEY, refKey);
					map.put(AuthenImproveConstant.SESSION_KEY.DIRECT_DEBIT_RESPONSE, directDebitLoginResponse);

					session.setAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE, map);
					logger.info("cardnumber :"+ cardnumber );
					improceService.nextRegisterFlow(session, AuthenImproveConstant.REGISTER_STAGE.SETPIN);
				}
			} else {
				if ("K0076".equals(directDebitLoginResponse.getAuthenUser().getErrorCode())) {
					request.setAttribute("MessageCode", "96");
				} else {
					if(!directDebitLoginResponse.getPermit()) {
						request.setAttribute("MessageCode", "96");
					}
				}

				throw new AuthenFailedException("Authen failed");
			}

		} catch (FlowFailedException e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		} catch (UpdateDataException e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		} catch (AuthenFailedException e) {
			forwardName = "back";
			request.setAttribute("registUserType", userType);
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		}

		return forward(forwardName, mapping);
	}

	public ActionForward setpin(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		String forwardName = "success";
		logger.info("setpin");
		String username = request.getParameter("username");
		String password = request.getParameter("password");
		String userType = request.getParameter("userType");
		logger.info("user name : {} password : {} userType : {}", username, password, userType);
		AuthenImproveService improceService = new AuthenImproveService();


		try {
			HttpSession session = request.getSession();

			if (StringUtils.isEmpty(username) && StringUtils.isEmpty(password)) {
				request.setAttribute("MessageCode", "98");
				throw new AuthenFailedException("Authen failed");
			}


			Timestamp accessAuthenTime = new Timestamp(System.currentTimeMillis());
			KOLLoginResponse kolLoginResponse = improceService.authenticateUser(username, password, userType);
			Timestamp completeAuthenTime = new Timestamp(System.currentTimeMillis());
			logger.info(kolLoginResponse.toString());

			Map<String, Object> map = (Map<String, Object>) session.getAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE);

			String refKey = (String) map.get(AuthenImproveConstant.SESSION_KEY.REF_KEY);
			logger.info("refKey {}", refKey);

			//logging
			EpayAuthLogRequest epayAuthLogRequest = improceService.getEPayAuthLogRequestByRefKey(refKey);
			if(null != epayAuthLogRequest) {
				String authenRequest = "{\"username\": "+username+", \"password\": "+password+"}";

				epayAuthLogRequest.setInputParam(authenRequest);

				EpayAuthLogAccessApi epayAuthLogAccessApi = new EpayAuthLogAccessApi();
				epayAuthLogAccessApi.setRequestId(epayAuthLogRequest.getRequestId());
				epayAuthLogAccessApi.setApiName(AuthenImproveConstant.API_NAME.AUTHEN_KBOL);
				epayAuthLogAccessApi.setInputParam(authenRequest);
				epayAuthLogAccessApi.setAccessDate(accessAuthenTime);
				epayAuthLogAccessApi.setCompleteDate(completeAuthenTime);
				epayAuthLogAccessApi.setReturnCode("00");
				kolLoginResponse.setAuthen(true);
				if(!kolLoginResponse.getAuthen()) {
					String textFailed = "Authen failed";
					epayAuthLogAccessApi.setReturnCode("99");
					epayAuthLogAccessApi.setReturnException(textFailed);
					epayAuthLogRequest.setRequestStatus(AuthenImproveConstant.REQUEST_STATUS.FAIL);
					epayAuthLogRequest.setRemark(textFailed);
				} else {
					ObjectMapper mapper = new ObjectMapper();
					String responseAuthen = mapper.writeValueAsString(kolLoginResponse);
					epayAuthLogAccessApi.setReturnParam(responseAuthen);
				}

				//insert log
				try{
					logger.info("inserting log");
					improceService.insertEPayLogAccessApi(epayAuthLogAccessApi);
					improceService.updateEPayAuthLogRequest(epayAuthLogRequest);
				} catch (Exception e) {
					logger.info("inserting log error");
					e.printStackTrace();
				}
			}

			if(kolLoginResponse.getAuthen()){
				logger.info("transformAuthenProfile");
				//EpayAuthProfile epayAuthProfile = transformAuthenProfile(kolLoginResponse, userType, username);

				logger.info("check already exist");
//				EpayAuthProfile isExistProfile = improceService.getEpayAuthProfileByMobileNoAndCitizenId(epayAuthProfile.getMobileNo(), epayAuthProfile.getCitizenId());
//				if(null != isExistProfile) {
//
//					request.setAttribute("MessageCode", "99");
//
//					throw new Exception("Already regist");
//				} else {
//
//					if(!kolLoginResponse.getPermit()) {
//						request.setAttribute("MessageCode", "96");
//						throw new AuthenFailedException("No permission");
//					}

//					logger.info("insertEpayAuthProfile");
//					map.put(AuthenImproveConstant.SESSION_KEY.USER_NAME, username);
//					map.put(AuthenImproveConstant.SESSION_KEY.MOBILE, kolLoginResponse.getMobileNumber());
//					map.put(AuthenImproveConstant.SESSION_KEY.ACCOUNT, kolLoginResponse.getMainAccount().getAccountNo());
//					map.put(AuthenImproveConstant.SESSION_KEY.USER_TYPE, userType);
//					logger.info("setProfileId");
//					map.put(AuthenImproveConstant.SESSION_KEY.EPAY_DTO, epayAuthProfile);
//
//					map.put(AuthenImproveConstant.SESSION_KEY.REF_KEY, refKey);
//					map.put(AuthenImproveConstant.SESSION_KEY.KOL_RESPONSE, kolLoginResponse);
//
//					session.setAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE, map);
//					logger.info("username :"+ username );
//					improceService.nextRegisterFlow(session, AuthenImproveConstant.REGISTER_STAGE.SETPIN);
					logger.info("insertEpayAuthProfile");
					map.put(AuthenImproveConstant.SESSION_KEY.USER_NAME, "USER_TEST");
					map.put(AuthenImproveConstant.SESSION_KEY.MOBILE, "0867499956");
					map.put(AuthenImproveConstant.SESSION_KEY.ACCOUNT, "6665558881");
					map.put(AuthenImproveConstant.SESSION_KEY.USER_TYPE, "1");
					logger.info("setProfileId");
					//map.put(AuthenImproveConstant.SESSION_KEY.EPAY_DTO, epayAuthProfile);

					map.put(AuthenImproveConstant.SESSION_KEY.REF_KEY, "Abcdefghijk");
					//map.put(AuthenImproveConstant.SESSION_KEY.KOL_RESPONSE, kolLoginResponse);

					session.setAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE, map);
					logger.info("username :"+ username );
					improceService.nextRegisterFlow(session, AuthenImproveConstant.REGISTER_STAGE.SETPIN);
				}
			/*} else {
				if ("K0076".equals(kolLoginResponse.getAuthenUser().getErrorCode())) {
					request.setAttribute("MessageCode", "96");
				} else {
					if(!kolLoginResponse.getPermit()) {
						request.setAttribute("MessageCode", "96");
					}
				}

				throw new AuthenFailedException("Authen failed");
			}*/

		} catch (FlowFailedException e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
//		} catch (UpdateDataException e) {
//			forwardName = "failed";
//			request.setAttribute("MessageDesc", e.getMessage());
//			e.printStackTrace();
		} catch (AuthenFailedException e) {
			forwardName = "back";
			request.setAttribute("registUserType", userType);
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		}

		return forward(forwardName, mapping);
	}

	public ActionForward resend(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		String forwardName = "success";
		logger.info("resend");

		try {
			HttpSession session = request.getSession();
			Map<String, Object> profile = (Map<String, Object>) session.getAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE);
			EpayAuthProfile epayDTO = (EpayAuthProfile) profile.get(AuthenImproveConstant.SESSION_KEY.EPAY_DTO);

			AuthenImproveService improceService = new AuthenImproveService();

			improceService.nextRegisterFlow(session, AuthenImproveConstant.REGISTER_STAGE.OTP);

			String userName = (String) profile.get(AuthenImproveConstant.SESSION_KEY.USER_NAME);
			String userType = (String) profile.get(AuthenImproveConstant.SESSION_KEY.USER_TYPE);
			String otpProfile = "";
			ws.kol.AuthenUserEntity oldEntity = new ws.kol.AuthenUserEntity();
			if(userType.equals(KOLWSConstants.KMA)) {
				AuthenImproveRequest kmaProfile = (AuthenImproveRequest) profile.get(AuthenImproveConstant.SESSION_KEY.KMA_BODY);
				String countryCode = kmaProfile.getData().getMobileCountryCode();
				logger.info("countryCode {}",countryCode);
				boolean isLocalMobileNo = (countryCode == null || countryCode.length() == 0 || "66".equals(countryCode));
				otpProfile = improceService.getOtpProfile(isLocalMobileNo, userType);
				logger.info("otpProfile {}",otpProfile);
				improceService.transformKMAEntityToOldAuthenEntity(kmaProfile, oldEntity);
			} else {
				KOLLoginResponse kolLoginResponse = (KOLLoginResponse) profile.get(AuthenImproveConstant.SESSION_KEY.KOL_RESPONSE);
				AuthenUserEntity userEntity = kolLoginResponse.getAuthenUser();
				String countryCode = userEntity.getMobileCountryCode();
				logger.info("countryCode {}",countryCode);
				boolean isLocalMobileNo = (countryCode == null || countryCode.length() == 0 || "66".equals(countryCode));
				otpProfile = improceService.getOtpProfile(isLocalMobileNo, userType);
				logger.info("otpProfile {}",otpProfile);
				//firing OTP
				improceService.transformNewAuthenEntityToOldAuthenEntity(userEntity, oldEntity);

			}
			Timestamp accessOtpTime = new Timestamp(System.currentTimeMillis());
			ApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getServletContext());
			OtpService otpService = ctx.getBean(OtpService.class);
			Map<String, String> map = otpService.genOTPByWS(otpProfile, userName, oldEntity, userType);
			Timestamp completeOtpime = new Timestamp(System.currentTimeMillis());
			String responseCode = map.get(ConstantsTransaction.TRANS_CALL_OTP_RESPONSE_CODE);
			String refNo = map.get(ConstantsTransaction.TRANS_CALL_OTP_REF_NO);
			logger.info("responseCode {}",responseCode);
			logger.info("refNo {}",refNo);

			//logging
			String refKey = (String) profile.get(AuthenImproveConstant.SESSION_KEY.REF_KEY);
			EpayAuthLogRequest epayAuthLogRequest = improceService.getEPayAuthLogRequestByRefKey(refKey);
			if(null != epayAuthLogRequest) {
				ObjectMapper mapper = new ObjectMapper();
				String oldEntityRequest = mapper.writeValueAsString(oldEntity);
				String otpRequest = "{\"otpProfile\": "+otpProfile+", \"userName\": "+userName+", \"oldEntity\": "+oldEntityRequest+" }";


				EpayAuthLogAccessApi epayAuthLogAccessApi = new EpayAuthLogAccessApi();
				epayAuthLogAccessApi.setRequestId(epayAuthLogRequest.getRequestId());
				epayAuthLogAccessApi.setApiName(AuthenImproveConstant.API_NAME.OTP);
				epayAuthLogAccessApi.setInputParam(otpRequest);
				epayAuthLogAccessApi.setAccessDate(accessOtpTime);
				epayAuthLogAccessApi.setCompleteDate(completeOtpime);
				epayAuthLogAccessApi.setReturnCode("00");
				if (!ConstantsPayment.RESPONSE_SUCCESS.equals(responseCode)) {
					String textFailed = "Otp Failed";
					epayAuthLogAccessApi.setReturnCode("99");
					epayAuthLogAccessApi.setReturnException(textFailed);
					epayAuthLogRequest.setRequestStatus(AuthenImproveConstant.REQUEST_STATUS.FAIL);
					epayAuthLogRequest.setRemark(textFailed);
				} else {
					String otpResponse = mapper.writeValueAsString(map);
					epayAuthLogAccessApi.setReturnParam(otpResponse);
				}

				//insert log
				try{
					logger.info("inserting log");
					improceService.insertEPayLogAccessApi(epayAuthLogAccessApi);
					improceService.updateEPayAuthLogRequest(epayAuthLogRequest);
				} catch (Exception e) {
					logger.info("inserting log error");
					e.printStackTrace();
				}
			}

			session.setAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE, profile);
			if (StringUtils.isBlank(responseCode)) {
				logger.info("Empty response code");
				throw new Exception("OTP Error");
			}
			if (ConstantsPayment.RESPONSE_SUCCESS.equals(responseCode)) {

				logger.info("Request OTP success with response code: {}", responseCode);

				if (StringUtils.isBlank(refNo)) {
					logger.warn("Empty ref no");
					throw new Exception("Empty ref no");
				}
				profile.put(AuthenImproveConstant.SESSION_KEY.OTP_TRY, 0);
				profile.put(AuthenImproveConstant.SESSION_KEY.OTP_REF, refNo);
			} else {
				logger.info("Request OTP fail with response code: {}", responseCode);
				throw new Exception("Request OTP fail with response code:"+responseCode);
			}
		} catch (FlowFailedException e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		}

		return forward(forwardName, mapping);
	}
	public ActionForward otp(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		String forwardName = "success";
		logger.info("otp");
		String pin1 = request.getParameter("pin1");
		String pin2 = request.getParameter("pin2");
		HttpSession session = request.getSession();
		Map<String, Object> profile = (Map<String, Object>) session.getAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE);
		try{
			AuthenImproveService improceService = new AuthenImproveService();

			improceService.nextRegisterFlow(session, AuthenImproveConstant.REGISTER_STAGE.OTP);

			int pinResult = improceService.isPinValid(pin1, pin2);
			if(pinResult == 0) {

				EpayAuthProfile epayDTO = (EpayAuthProfile) profile.get(AuthenImproveConstant.SESSION_KEY.EPAY_DTO);

				//encrypt

				logger.info("encrypt");
				String encrypted = improceService.encrypt(pin1);
				logger.info("pin encrypted {}",encrypted);
				epayDTO.setPin(encrypted);
				profile.put(AuthenImproveConstant.SESSION_KEY.EPAY_DTO, epayDTO);

				String userName = (String) profile.get(AuthenImproveConstant.SESSION_KEY.USER_NAME);
				String userType = (String) profile.get(AuthenImproveConstant.SESSION_KEY.USER_TYPE);
				String otpProfile = "";
				ws.kol.AuthenUserEntity oldEntity = new ws.kol.AuthenUserEntity();
				if(userType.equals(KOLWSConstants.KMA)) {
					AuthenImproveRequest kmaProfile = (AuthenImproveRequest) profile.get(AuthenImproveConstant.SESSION_KEY.KMA_BODY);
					String countryCode = kmaProfile.getData().getMobileCountryCode();
					logger.info("countryCode {}",countryCode);
					boolean isLocalMobileNo = (countryCode == null || countryCode.length() == 0 || "66".equals(countryCode));
					otpProfile = improceService.getOtpProfile(isLocalMobileNo, userType);
					logger.info("otpProfile {}",otpProfile);
					improceService.transformKMAEntityToOldAuthenEntity(kmaProfile, oldEntity);
				} else if (userType.equals(KOLWSConstants.DEBIT_CARD)) {
					DirectDebitLoginResponse directDebitLoginResponse = (DirectDebitLoginResponse) profile.get(AuthenImproveConstant.SESSION_KEY.DIRECT_DEBIT_RESPONSE);
					DirectDebitAuthenUserEntity userEntity = directDebitLoginResponse.getAuthenUser();
					String countryCode = userEntity.getMobileCountryCode();
					logger.info("countryCode {}",countryCode);
					boolean isLocalMobileNo = (countryCode == null || countryCode.length() == 0 || "66".equals(countryCode));
					otpProfile = improceService.getOtpProfile(isLocalMobileNo, userType);
					logger.info("otpProfile {}",otpProfile);
					//firing OTP
					improceService.transformNewAuthenEntityToOldAuthenEntity(userEntity, oldEntity);
				} else {
					KOLLoginResponse kolLoginResponse = (KOLLoginResponse) profile.get(AuthenImproveConstant.SESSION_KEY.KOL_RESPONSE);
					AuthenUserEntity userEntity = kolLoginResponse.getAuthenUser();
					String countryCode = userEntity.getMobileCountryCode();
					logger.info("countryCode {}",countryCode);
					boolean isLocalMobileNo = (countryCode == null || countryCode.length() == 0 || "66".equals(countryCode));
					otpProfile = improceService.getOtpProfile(isLocalMobileNo, userType);
					logger.info("otpProfile {}",otpProfile);
					//firing OTP
					improceService.transformNewAuthenEntityToOldAuthenEntity(userEntity, oldEntity);

				}
				Timestamp accessOtpTime = new Timestamp(System.currentTimeMillis());
				ApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getServletContext());
				OtpService otpService = ctx.getBean(OtpService.class);
				Map<String, String> map = otpService.genOTPByWS(otpProfile, userName, oldEntity, userType);
				Timestamp completeOtpime = new Timestamp(System.currentTimeMillis());
				String responseCode = map.get(ConstantsTransaction.TRANS_CALL_OTP_RESPONSE_CODE);
				String refNo = map.get(ConstantsTransaction.TRANS_CALL_OTP_REF_NO);
				logger.info("responseCode {}",responseCode);
				logger.info("refNo {}",refNo);

				//logging
				String refKey = (String) profile.get(AuthenImproveConstant.SESSION_KEY.REF_KEY);
				EpayAuthLogRequest epayAuthLogRequest = improceService.getEPayAuthLogRequestByRefKey(refKey);
				if(null != epayAuthLogRequest) {
					ObjectMapper mapper = new ObjectMapper();
					String oldEntityRequest = mapper.writeValueAsString(oldEntity);
					String otpRequest = "{\"otpProfile\": "+otpProfile+", \"userName\": "+userName+", \"oldEntity\": "+oldEntityRequest+" }";

					EpayAuthLogAccessApi epayAuthLogAccessApi = new EpayAuthLogAccessApi();
					epayAuthLogAccessApi.setRequestId(epayAuthLogRequest.getRequestId());
					epayAuthLogAccessApi.setApiName(AuthenImproveConstant.API_NAME.OTP);
					epayAuthLogAccessApi.setInputParam(otpRequest);
					epayAuthLogAccessApi.setAccessDate(accessOtpTime);
					epayAuthLogAccessApi.setCompleteDate(completeOtpime);
					epayAuthLogAccessApi.setReturnCode("00");
					if (!ConstantsPayment.RESPONSE_SUCCESS.equals(responseCode)) {
						String textFailed = "Otp Failed";
						epayAuthLogAccessApi.setReturnCode("99");
						epayAuthLogAccessApi.setReturnException(textFailed);
						epayAuthLogRequest.setRequestStatus(AuthenImproveConstant.REQUEST_STATUS.FAIL);
						epayAuthLogRequest.setRemark(textFailed);
					} else {
						String otpResponse = mapper.writeValueAsString(map);
						epayAuthLogAccessApi.setReturnParam(otpResponse);
					}

					//insert log
					try{
						logger.info("inserting log");
						improceService.insertEPayLogAccessApi(epayAuthLogAccessApi);
						improceService.updateEPayAuthLogRequest(epayAuthLogRequest);
					} catch (Exception e) {
						logger.info("inserting log error");
						e.printStackTrace();
					}
				}

				session.setAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE, profile);
				if (StringUtils.isBlank(responseCode)) {
					logger.info("Empty response code");
					throw new Exception("OTP Error");
				}
				if (ConstantsPayment.RESPONSE_SUCCESS.equals(responseCode)) {

					logger.info("Request OTP success with response code: {}", responseCode);

					if (StringUtils.isBlank(refNo)) {
						logger.warn("Empty ref no");
						throw new Exception("Empty ref no");
					}
					profile.put(AuthenImproveConstant.SESSION_KEY.OTP_TRY, 0);
					profile.put(AuthenImproveConstant.SESSION_KEY.OTP_REF, refNo);
				} else {
					logger.info("Request OTP fail with response code: {}", responseCode);
					throw new Exception("Request OTP fail with response code:"+responseCode);
				}

			} else {
				request.setAttribute("MessageCode", String.valueOf(pinResult));
				throw new Exception("Pin not correct");
			}



		} catch (FlowFailedException e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			forwardName = "repin";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		}

		return forward(forwardName, mapping);
	}

	public ActionForward done(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		String forwardName = "success";
		logger.info("done");
		try {
			HttpSession session = request.getSession();
			Map<String, Object> profile = (Map<String, Object>) session.getAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE);
			String userName = (String) profile.get(AuthenImproveConstant.SESSION_KEY.USER_NAME);
			String userType = (String) profile.get(AuthenImproveConstant.SESSION_KEY.USER_TYPE);
			String refNo = (String) profile.get(AuthenImproveConstant.SESSION_KEY.OTP_REF);
			logger.info("refNo {}", refNo);
			String otp = request.getParameter("otp");
			logger.info("otp :" + otp);
			AuthenUserEntity userEntity = new AuthenUserEntity();
			AuthenImproveRequest kmaProfile = null;


			AuthenImproveService improceService = new AuthenImproveService();

			int otpTry = 0;
			if (null != profile.get(AuthenImproveConstant.SESSION_KEY.OTP_TRY)) {
				otpTry = (Integer) profile.get(AuthenImproveConstant.SESSION_KEY.OTP_TRY);
			}
			if (otpTry > ConstantsPayment.COUNT_LOGIN_TIME) {
				logger.info("OTPTry exceed");
				request.setAttribute("MessageCode", "1");
				throw new Exception("OTPTry exceed");
			}

			if (KOLWSConstants.KMA.equals(userType)) {
				kmaProfile = (AuthenImproveRequest) profile.get(AuthenImproveConstant.SESSION_KEY.KMA_BODY);
				String countryCode = kmaProfile.getData().getMobileCountryCode();
				userEntity.setMobileCountryCode(countryCode);
			} else if (KOLWSConstants.DEBIT_CARD.equals(userType)) {
				DirectDebitLoginResponse directDebitLoginResponse = (DirectDebitLoginResponse) profile.get(AuthenImproveConstant.SESSION_KEY.DIRECT_DEBIT_RESPONSE);
				DirectDebitAuthenUserEntity newUserEntity = directDebitLoginResponse.getAuthenUser();
				improceService.transformNewAuthenEntityToOldAuthenEntity(newUserEntity, userEntity);
			} else {
				KOLLoginResponse kolLoginResponse = (KOLLoginResponse) profile.get(AuthenImproveConstant.SESSION_KEY.KOL_RESPONSE);
				userEntity = kolLoginResponse.getAuthenUser();
			}



			improceService.nextRegisterFlow(session, AuthenImproveConstant.REGISTER_STAGE.DONE);

			logger.info("verifyOTPByWS");
			Timestamp accessOtpTime = new Timestamp(System.currentTimeMillis());
			Map<String, String> map = improceService.verifyOtp(userName, userType, refNo, otp, userEntity);
			Timestamp completeOtpTime = new Timestamp(System.currentTimeMillis());

			if (map == null || map.isEmpty()) {
				logger.info("Empty OTP result");
				request.setAttribute("MessageCode", "2");
				throw new Exception("Empty OTP result");
			}

			String responseCode = map.get(ConstantsTransaction.TRANS_CALL_OTP_RESPONSE_CODE);
			String responseDesc = map.get(ConstantsTransaction.TRANS_CALL_OTP_RESPONSE_DESC);

			if (StringUtils.isBlank(responseCode)) {
				logger.info("Empty response code");
				request.setAttribute("MessageCode", "3");
				throw new Exception("Empty response code");
			}

			//logging
			String refKey = (String) profile.get(AuthenImproveConstant.SESSION_KEY.REF_KEY);
			EpayAuthLogRequest epayAuthLogRequest = improceService.getEPayAuthLogRequestByRefKey(refKey);
			if (null != epayAuthLogRequest) {
				ObjectMapper mapper = new ObjectMapper();
				String oldEntityRequest = mapper.writeValueAsString(userEntity);
				String otpRequest = "{\"refNo\": " + refNo + ", \"userName\": " + userName + ", \"oldEntity\": " + oldEntityRequest + " }";


				EpayAuthLogAccessApi epayAuthLogAccessApi = new EpayAuthLogAccessApi();
				epayAuthLogAccessApi.setRequestId(epayAuthLogRequest.getRequestId());
				epayAuthLogAccessApi.setApiName(AuthenImproveConstant.API_NAME.OTP);
				epayAuthLogAccessApi.setInputParam(otpRequest);
				epayAuthLogAccessApi.setAccessDate(accessOtpTime);
				epayAuthLogAccessApi.setCompleteDate(completeOtpTime);
				epayAuthLogAccessApi.setReturnCode("00");
				if (!ConstantsPayment.RESPONSE_SUCCESS.equals(responseCode)) {
					String textFailed = "VERIFY Otp Failed";
					epayAuthLogAccessApi.setReturnCode("99");
					epayAuthLogAccessApi.setReturnException(textFailed);
					epayAuthLogRequest.setRequestStatus(AuthenImproveConstant.REQUEST_STATUS.FAIL);
					epayAuthLogRequest.setRemark(textFailed);
				} else {
					String otpResponse = mapper.writeValueAsString(map);
					epayAuthLogAccessApi.setReturnParam(otpResponse);
					epayAuthLogRequest.setRequestStatus(AuthenImproveConstant.REQUEST_STATUS.SUCCESS);
				}

				//insert log
				try {
					logger.info("inserting log");
					improceService.insertEPayLogAccessApi(epayAuthLogAccessApi);
					improceService.updateEPayAuthLogRequest(epayAuthLogRequest);
				} catch (Exception e) {
					logger.info("inserting log error");
					e.printStackTrace();
				}
			}
			logger.info("Verify OTP success with response code: {}, desc: {}", responseCode, responseDesc);
			if ("00".equals(responseCode)) {
				EpayAuthProfile epayDTO = (EpayAuthProfile) profile.get(AuthenImproveConstant.SESSION_KEY.EPAY_DTO);
				logger.info("insertEpayAuthProfile");
				Long epayProfilePk = improceService.insertEpayAuthProfile(epayDTO);
				logger.info("pk {}", epayProfilePk);
				if (KOLWSConstants.KMA.equals(userType)) {
					for (AccountList a : kmaProfile.getData().getAccountList()) {
						Long mappingPk = improceService.insertMappingAccount(epayProfilePk, a.getAccoountNo());
						logger.info("mappingPk {}", mappingPk);
					}
				}

//				sendSMS
				try {
					Locale lang = (Locale) session.getAttribute("org.apache.struts.action.LOCALE");
					logger.info("lang {}", lang);
					String langCode = "TH";
					String message = "คุณได้ลงทะเบียนใช้งาน Krungsri e-Payment เรียบร้อย";
					if (lang.equals(Locale.US)) {
						langCode = "EN";
						message = "You have successfully register Krungsri e-Payment user.";
					}

					String countryCode = epayDTO.getMobilContryCode();
					String mobilNo = epayDTO.getMobileNo();
					String xMobilNo = "";
					logger.info("countryCode {} mobilNo {}", countryCode, mobilNo);
					int length = mobilNo.length();
					if (mobilNo.startsWith("0")) {
						xMobilNo = countryCode + mobilNo.substring((length - (length - 1)));
					}

					improceService.sendSMS(langCode, xMobilNo, message);
				} catch (Exception e) {
					logger.info("Send SMS ERROR {}", e.getMessage());
				}

			} else {

				otpTry += 1;
				profile.put(AuthenImproveConstant.SESSION_KEY.OTP_TRY, otpTry);

				//expired
				//fail
				if ("fail".equals(responseDesc)) {
					if (otpTry > ConstantsPayment.COUNT_LOGIN_TIME) {
						logger.info("OTPTry exceed");
						throw new Exception("OTPTry exceed");
					} else {
						logger.info("OTPTry reinput again");
						//setDescription(request,transactionSDO);
						request.setAttribute("MessageCode", responseCode);
						request.setAttribute("MessageDesc", responseDesc);
						forwardName = "reotp";
					}
				} else if ("expired".equals(responseDesc)) {
					logger.info("expired reinput again");
					//setDescription(request,transactionSDO);
					request.setAttribute("MessageCode", responseCode);
					request.setAttribute("MessageDesc", responseDesc);
					forwardName = "reotp";
				}
			}
		} catch (FlowFailedException e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		} catch (UpdateDataException e) {
			forwardName = "failed";
			request.setAttribute("MessageCode", "98");
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			forwardName = "failed";
			request.setAttribute("MessageDesc", e.getMessage());
			e.printStackTrace();
		}
		return forward(forwardName, mapping);
	}
	public ActionForward reback(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		logger.info("reback");

		request.getSession().removeAttribute(AuthenImproveConstant.EPAYMENT_FLOW.REGISTER);
		request.getSession().removeAttribute(AuthenImproveConstant.EPAYMENT_FLOW.CHANGE);
		request.getSession().removeAttribute(AuthenImproveConstant.EPAYMENT_FLOW.FORGOT);
		request.getSession().removeAttribute(AuthenImproveConstant.PROFILE_KEY.CAPTCHA_FAILED);
		request.getSession().removeAttribute(AuthenImproveConstant.PROFILE_KEY.OLD_PIN_FAILED);

		return forward("success", mapping);
	}

	public ActionForward failed(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		logger.info("failed");
		HttpSession session = request.getSession();
		String alreadyRegist = (String) session.getAttribute(AuthenImproveConstant.SESSION_KEY.FAIL_KMA_ALREADY_REGISTERED);
		if(null != alreadyRegist){
			request.setAttribute("MessageCode", "990808");
		}

		return forward("failed", mapping);
	}


	private EpayAuthProfile transformKMAProfile(AuthenImproveRequest kmaBody, String userType, String username) {
		EpayAuthProfile result = new EpayAuthProfile();
		result.setChannel(userType);
		result.setCitizenId(kmaBody.getData().getCitizenId());
		result.setTaxId("-");
		result.setMobileNo(kmaBody.getData().getMobileNo());
		result.setPin(null);
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
		Date dob1 = new Date();
		try {
			dob1 = dateFormat.parse(kmaBody.getData().getDateOfBirth());
		} catch (ParseException e) {
			e.printStackTrace();
		}
		java.sql.Date dob = new java.sql.Date(dob1.getTime());
		result.setDateOfBirth(dob);
		result.setEmail(kmaBody.getData().getEmail());
		result.setAccountType("INDIVIDUAL");
		result.setPaymentPermission("Y");

		result.setIsActive("Y");
		Date date = new Date();
		Timestamp timestamp =new Timestamp(date.getTime());
		result.setCreateDate(timestamp);
		result.setCreateBy("EpaySystem");
		result.setUpdateDate(timestamp);
		result.setUpdateBy("EpaySystem");

		result.setUserName(username);
		result.setOtpToken("0");
		result.setOtpTokenSerial("-");
		result.setMobilContryCode(kmaBody.getData().getMobileCountryCode());

		result.setFirstNameEN("-");
		result.setLastNameEN("-");
		result.setFirstNameTH("-");
		result.setLastNameTH("-");

		return result;
	}

	private EpayAuthProfile transformAuthenProfile(DirectDebitLoginResponse directDebitLoginResponse, String userType, String username) {
		EpayAuthProfile result = new EpayAuthProfile();
		result.setChannel(userType);
		result.setCitizenId(directDebitLoginResponse.getAuthenUser().getCitizenId());
		if(directDebitLoginResponse.getAuthenUser().isCorporate()) {
			result.setTaxId(directDebitLoginResponse.getAuthenUser().getTaxId());
		}
		result.setMobileNo(directDebitLoginResponse.getMobileNumber());
		result.setPin(null);
		Calendar calendar = directDebitLoginResponse.getAuthenUser().getDateOfBirth();
		java.sql.Date dob = new java.sql.Date(calendar.getTime().getTime());
		result.setDateOfBirth(dob);
		result.setEmail(directDebitLoginResponse.getAuthenUser().getEmail());
		String isActive = "N";
		if("ACTIVE".equals(directDebitLoginResponse.getAuthenUser().getUserStatus())) {
			isActive = "Y";
		}
		if(directDebitLoginResponse.getAuthenUser().isCorporate()) {
			result.setAccountType("CORPORATE");
		} else {
			result.setAccountType("INDIVIDUAL");
		}

		if (directDebitLoginResponse.getPermit()) {
			result.setPaymentPermission("Y");
		}

		result.setIsActive(isActive);
		Date date = new Date();
		Timestamp timestamp =new Timestamp(date.getTime());
		result.setCreateDate(timestamp);
		result.setCreateBy("EpaySystem");
		result.setUpdateDate(timestamp);
		result.setUpdateBy("EpaySystem");

		result.setUserName(username);
		result.setOtpToken(directDebitLoginResponse.getAuthenUser().getToken());
		result.setOtpTokenSerial(directDebitLoginResponse.getAuthenUser().getTokenSerial());
		result.setMobilContryCode(directDebitLoginResponse.getAuthenUser().getMobileCountryCode());

		result.setFirstNameEN(directDebitLoginResponse.getAuthenUser().getFirstNameEN());
		result.setLastNameEN(directDebitLoginResponse.getAuthenUser().getLastNameEN());
		result.setFirstNameTH(directDebitLoginResponse.getAuthenUser().getFirstNameTH());
		result.setLastNameTH(directDebitLoginResponse.getAuthenUser().getLastNameTH());

		return result;
	}

	private EpayAuthProfile transformAuthenProfile(KOLLoginResponse kolLoginResponse, String userType, String username) {
		EpayAuthProfile result = new EpayAuthProfile();
		result.setChannel(userType);
		result.setCitizenId(kolLoginResponse.getAuthenUser().getCitizenId());
		if(kolLoginResponse.getAuthenUser().isIsCorporate()) {
			result.setTaxId(kolLoginResponse.getAuthenUser().getTaxId());
		}
		result.setMobileNo(kolLoginResponse.getMobileNumber());
		result.setPin(null);
		Calendar calendar = kolLoginResponse.getAuthenUser().getDateOfBirth();
		java.sql.Date dob = new java.sql.Date(calendar.getTime().getTime());
		result.setDateOfBirth(dob);
		result.setEmail(kolLoginResponse.getAuthenUser().getEmail());
		String isActive = "N";
		if("ACTIVE".equals(kolLoginResponse.getAuthenUser().getUserStatus())) {
			isActive = "Y";
		}
		if(kolLoginResponse.getAuthenUser().isIsCorporate()) {
			result.setAccountType("CORPORATE");
		} else {
			result.setAccountType("INDIVIDUAL");
		}

		if (kolLoginResponse.getPermit()) {
			result.setPaymentPermission("Y");
		}

		result.setIsActive(isActive);
		Date date = new Date();
		Timestamp timestamp =new Timestamp(date.getTime());
		result.setCreateDate(timestamp);
		result.setCreateBy("EpaySystem");
		result.setUpdateDate(timestamp);
		result.setUpdateBy("EpaySystem");

		result.setUserName(username);
		result.setOtpToken(kolLoginResponse.getAuthenUser().getToken());
		result.setOtpTokenSerial(kolLoginResponse.getAuthenUser().getTokenSerial());
		result.setMobilContryCode(kolLoginResponse.getAuthenUser().getMobileCountryCode());

		result.setFirstNameEN(kolLoginResponse.getAuthenUser().getFirstNameEN());
		result.setLastNameEN(kolLoginResponse.getAuthenUser().getLastNameEN());
		result.setFirstNameTH(kolLoginResponse.getAuthenUser().getFirstNameTH());
		result.setLastNameTH(kolLoginResponse.getAuthenUser().getLastNameTH());

		return result;
	}

	public ActionForward checkIsReceiveInfo(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession();
		Map<String, Object> map = (Map<String, Object>) session.getAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE);

		String refKey = (String) map.get(AuthenImproveConstant.SESSION_KEY.REF_KEY);
		logger.info("refKey {}", refKey);

		String status = "";
		String message = "";

		AuthenImproveService improceService = new AuthenImproveService();
		try{
			EpayAuthLogRequest epayAuthLogRequest = improceService.getEPayAuthLogRequestByRefKey(refKey);
			if(null != epayAuthLogRequest){
				if(AuthenImproveConstant.REQUEST_STATUS.KMA_INSERT.equals(epayAuthLogRequest.getRequestStatus())){
					status = "00";
					message = "OK";

					String object = epayAuthLogRequest.getInputParam();
					ObjectMapper mapper = new ObjectMapper();
					AuthenImproveRequest kmaBody = mapper.readValue(object, AuthenImproveRequest.class);
					map.put(AuthenImproveConstant.SESSION_KEY.KMA_BODY, kmaBody);

					session.setAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE, map);

				} else if(AuthenImproveConstant.REQUEST_STATUS.REGISTERED.equals(epayAuthLogRequest.getRequestStatus())){
					status = "95";
					message = "Already Registered";
					session.setAttribute(AuthenImproveConstant.SESSION_KEY.FAIL_KMA_ALREADY_REGISTERED, status);
				} else {
					status = "90";
					message = "Not yet";
				}
			}
		} catch (PopulateDataException e) {
			status = "99";
			message = e.getMessage();
			e.printStackTrace();
		} catch (JsonParseException e) {
			status = "99";
			message = e.getMessage();
			e.printStackTrace();
		} catch (JsonMappingException e) {
			status = "99";
			message = e.getMessage();
			e.printStackTrace();
		} catch (IOException e) {
			status = "99";
			message = e.getMessage();
			e.printStackTrace();
		}

		StringBuilder sb =new StringBuilder();
		sb.append("  {  ");
		sb.append("      \"refKey\" : \""+refKey+"\",  ");
		sb.append("      \"status\" : \""+status+"\",  ");
		sb.append("      \"message\": \""+message+"\"  ");
		sb.append("  }  ");

		response.setContentType("application/json");
		try {
			OutputStream os = response.getOutputStream();
			os.write(sb.toString().getBytes());
			os.flush();
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	public ActionForward checkKmaVersion(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		logger.info("checkKmaVersion");

		String xRefKey = "";
		String xStatus = "";
		String xMessage = "";
		try {
			HttpSession session = request.getSession();

			PushNotiModel model = new PushNotiModel();
			model.setMobileNo(request.getParameter("mobileNo"));;
			model.setCountryCode(request.getParameter("countryCode"));
			model.setAuthType(AuthenImproveConstant.AUTH_TYPE_INT.REGISTER);

			logger.info("checkKmaVersion "+ model.getMobileNo());

			AuthenImproveService improceService = new AuthenImproveService();
			PushNotiModelResponse pushNotiModelResponse = improceService.getAppVersion(model);

			xStatus = pushNotiModelResponse.getCode();
			xMessage = pushNotiModelResponse.getMessage();

			if(null != pushNotiModelResponse.getError()) {
				xStatus = pushNotiModelResponse.getError().getCode();
				xMessage = pushNotiModelResponse.getError().getMessage();
			}

		} catch (Exception ex) {
			ex.printStackTrace();
			xStatus = "99";
			xMessage = ex.getMessage();
		}

		StringBuilder sb =new StringBuilder();
		sb.append("  {  ");
		sb.append("      \"status\" : \""+xStatus+"\",  ");
		sb.append("      \"message\": \""+xMessage+"\"  ");
		sb.append("  }  ");

		response.setContentType("application/json");
		try {
			OutputStream os = response.getOutputStream();
			os.write(sb.toString().getBytes());
			os.flush();
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
		}


		return null;
	}

	public ActionForward generateQR(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession();
		Map<String, Object> map = (Map<String, Object>) session.getAttribute(AuthenImproveConstant.PROFILE_KEY.PROFILE);

		String refKey = (String) map.get(AuthenImproveConstant.SESSION_KEY.REF_KEY);
		logger.info("refKey {}", refKey);

		StringBuilder sb =new StringBuilder();
		sb.append("  {  ");
		sb.append("      \"refSys\" : \"EPAY_AUTH\",  ");
		sb.append("      \"refKey\" : \""+refKey+"\",  ");
		sb.append("      \"refType\": \"01\"  ");
		sb.append("  }  ");


		try {
			String encrypted = AuthenImproveService.encrypt(sb.toString());

			QRCodeWriter barcodeWriter = new QRCodeWriter();
			BitMatrix bitMatrix = barcodeWriter.encode(encrypted, BarcodeFormat.QR_CODE, 200, 200);

			BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
			image.createGraphics();

			Graphics2D graphics = (Graphics2D) image.getGraphics();
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, 200, 200);
			graphics.setColor(Color.BLACK);

			for (int i = bitMatrix.getTopLeftOnBit()[0]; i < 200; i++) {
				for (int j = bitMatrix.getTopLeftOnBit()[1]; j < 200; j++) {
					if (bitMatrix.get(i, j)) {
						graphics.fillRect(i, j, 1, 1);
					}
				}
			}
			response.setContentType("image/png");
			OutputStream os = response.getOutputStream();

			ImageIO.write(image, "png", os);
		} catch (WriterException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}


		return null;
	}


}
