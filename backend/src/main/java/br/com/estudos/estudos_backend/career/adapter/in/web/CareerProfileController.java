package br.com.estudos.estudos_backend.career.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.estudos_backend.career.service.CareerProfileService;

@RestController
@RequestMapping("/api/career/profile")
public class CareerProfileController {

	private final CareerProfileService service;

	public CareerProfileController(CareerProfileService service) {
		this.service = service;
	}

	@GetMapping
	public CareerDto.ProfileResponse getProfile() {
		return CareerDto.ProfileResponse.from(service.getProfile());
	}

	@PutMapping
	public CareerDto.ProfileResponse updateProfile(@Valid @RequestBody CareerDto.ProfileRequest request) {
		return CareerDto.ProfileResponse.from(service.updateProfile(request.headline(), request.summary(),
				request.education(), request.experience(), request.projects(), request.certifications(),
				request.languages(), request.links()));
	}
}
