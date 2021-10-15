package de.gematik.test.tiger.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TigerAdminUiController {

    @GetMapping("/start")
    public String getStartPage() {
        return "startPage";
    }

    @GetMapping("/yml-page")
    public String getYmlPage(@RequestParam(name = "filename", defaultValue = "ERezept") String filename, Model model) {
        model.addAttribute("filename", filename);
        return "ymlPage";
    }
}
