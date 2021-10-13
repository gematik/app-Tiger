package de.gematik.test.tiger.admin.controller;

import lombok.Data;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;

@Data
@Controller
@Validated
public class TigerAdminUiController {

    @GetMapping("/start")
    public String getStartPage() {
        return "startPage";
    }
}
