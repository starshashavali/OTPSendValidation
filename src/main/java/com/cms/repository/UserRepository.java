package com.cms.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cms.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
	

	
	UserEntity findByFullName(String fullName);
	
	UserEntity findByFullNameAndPhoneNumber(String fullName, String phoneNumber);
	
	Optional<UserEntity> findByPhoneNumber(String phone);
	
    
    List<UserEntity> findBySubscriptionEndDateBefore(LocalDate date);
    
  
    
    List<UserEntity> findBySubscriptionEndDateBetween(LocalDate startDate, LocalDate endDate);

    
}
	
	

