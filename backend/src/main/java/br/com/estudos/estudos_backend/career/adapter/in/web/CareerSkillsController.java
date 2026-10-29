package br.com.estudos.estudos_backend.career.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.estudos_backend.career.service.CareerSkillsProfileService;
import br.com.estudos.estudos_backend.career.service.CareerSkillsProfileService.UserCareerSkillsProfile;

@RestController
@RequestMapping("/api/career/skills")
public class CareerSkillsController {

	private final CareerSkillsProfileService service;

	public CareerSkillsController(CareerSkillsProfileService service) {
		this.service = service;
	}

	@GetMapping
	public UserCareerSkillsProfile getSkillsProfile() {
		return service.getUserSkillsProfile();
	}
}
