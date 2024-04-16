package com.cms.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cms.binding.PersonCounts;
import com.cms.entity.PhoneOtp;
import com.cms.entity.UserEntity;
import com.cms.exception.UserNotValidException;
import com.cms.repository.PhoneOtpRepository;
import com.cms.repository.UserRepository;
import com.cms.utils.EmailUtils;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;

@Service
public class UserServiceImpl {
	
	
    private static final String API_KEY = "S4oGJgY1R20MTPEzaDL3wNKIqCkZVUABlWjdpH9nXrtbFOiy5uuFWL9YZMkmJlP7Azsjy6Xo18r2nCid";
    private static final String OTP_URL = "https://www.fast2sms.com/dev/bulkV2";

	

	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private EmailUtils emailUtils;
	
	@Autowired
	private PhoneOtpRepository phoneOtpRepo;
	
	
//save data

	public UserEntity savePerson(UserEntity person, MultipartFile imageFile) throws IOException {

		if (imageFile != null && !imageFile.isEmpty()) {
			person.setProfilePic(imageFile.getBytes());
		}
		

		simulatePayment(person);

		return userRepository.save(person);
	}

	
	//counts
	public PersonCounts getCountOfPersons() {
		List<UserEntity> allPersons = userRepository.findAll();

		Integer maleCount = (int) allPersons.stream().filter(person -> person.getGender().equalsIgnoreCase("Male"))
				.count();

		Integer femaleCount = (int) allPersons.stream().filter(person -> person.getGender().equalsIgnoreCase("Female"))
				.count();

		Integer childrenCount = (int) allPersons.stream().filter(person -> person.getAge() < 18).count();

		Integer seniorCitizenCount = (int) allPersons.stream().filter(person -> person.getAge() >= 60).count();

		return new PersonCounts(maleCount, femaleCount, childrenCount, seniorCitizenCount);
	}
	
	
	//otp send 

	public String sendOTPWithMessage(String fullName, String phoneNumber, String route) throws Exception {
	    UserEntity user = userRepository.findByFullNameAndPhoneNumber(fullName, phoneNumber);
	    if (user == null) {
	        return " User not found";
	    }

	    LocalDate currentDate = LocalDate.now();
	    if (user.getSubscriptionEndDate() != null && user.getSubscriptionEndDate().isBefore(currentDate)) {
	        return " Subscription expired or completed. Please renew your subscription to log in.";
	    }

	    String otpStatus = updateOrCreateOtp(phoneNumber, fullName, route);
	    return otpStatus + ";" + route;
	}

	private String updateOrCreateOtp(String phoneNumber, String fullName, String route) throws Exception {
	    Optional<PhoneOtp> existingOtp = phoneOtpRepo.findByPhoneNumber(phoneNumber);
	    if (existingOtp.isPresent()) {
	        PhoneOtp otp = existingOtp.get();
	        if (otp.getExpiryTime().isAfter(LocalDateTime.now())) {
	            return " OTP is still valid. Please check your mobile messages.";
	        } else {
	            return sendUpdatedOtp(otp, phoneNumber, route);
	        }
	    } else {
	        return sendNewOtp(phoneNumber, fullName, route);
	    }
	}

	private String sendUpdatedOtp(PhoneOtp otp, String phoneNumber, String route) throws Exception {
	    otp.setOtp(generateOTP());
	    otp.setExpiryTime(LocalDateTime.now().plusMinutes(3));
	    phoneOtpRepo.save(otp);
	    sendOtpMessage(phoneNumber, otp.getOtp(), route);
	    return " OTP sent successfully.";
	}

	private String sendNewOtp(String phoneNumber, String fullName, String route) throws Exception {
	    PhoneOtp newOtp = new PhoneOtp();
	    newOtp.setPhoneNumber(phoneNumber);
	    newOtp.setFullName(fullName);
	    newOtp.setOtp(generateOTP());
	    newOtp.setExpiryTime(LocalDateTime.now().plusMinutes(3));
	    phoneOtpRepo.save(newOtp);
	    sendOtpMessage(phoneNumber, newOtp.getOtp(), route);
	    return " OTP sent successfully.";
	}

	private void sendOtpMessage(String phoneNumber, String otp, String route) throws Exception {
	    String apiKey = API_KEY; 

	    HttpResponse<String> response = Unirest.post(OTP_URL)
	            .header("authorization", apiKey)
	            .header("Content-Type", "application/x-www-form-urlencoded")
	            .body("variables_values=" + otp + "&route=" + route + "&numbers=" + phoneNumber)
	            .asString();

	    System.out.println("OTP sent: " + response.getBody());
	}

	private String generateOTP() {
	    Random random = new Random();
	    int otp = 1000 + random.nextInt(9000);
	    return String.valueOf(otp);
	}


	// verify number and otp

	public String verifyOtp(String numbers, String otp) {
		PhoneOtp entity = phoneOtpRepo.findByPhoneNumberAndOtp(numbers, otp);
		if (entity != null) {
			return "Otp Verified";
		}
		return "Invalid OTP";

	}

	
	//fetch img
	public byte[] getImageDataById(Long id) {
		Optional<UserEntity> optinal = userRepository.findById(id);
		if (optinal.isPresent()) {
			UserEntity userEntity = optinal.get();
			return userEntity.getProfilePic();
		}
		 throw new UserNotValidException("UserNot found...");
	}


	private void simulatePayment(UserEntity person) {
		// dummy  payment API

		System.out.println("Dummy payment processed for user: " + person.getFullName() + ", Amount: "
				+ person.getPrice() + " USD");

		person.setPaidAmount(person.getPrice());
	}
	
	
	//send remainder msg to mail
	@Scheduled(cron = "0 0 10 * * *")//every day at 10:00 AM
	public void sendSubscriptionExpirationNotifications() throws Exception {
        System.out.println("Request to :: Notification...");
        
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(10);
        List<UserEntity> users = userRepository.findBySubscriptionEndDateBetween(startDate, endDate);
        
        for (UserEntity user : users) {
            LocalDate subscriptionEndDate = user.getSubscriptionEndDate();
            System.out.println("Sending renewal notification for user: " + user.getId());
            String emailMessage = "Dear " + user.getFullName() + ", Your subscription will expire on " + subscriptionEndDate.toString() + ". Please renew your subscription.";
            
            emailUtils.sendEmail(user.getEmail(), "Subscription Renewal Notification", emailMessage);
        }
    }
}