package com.hackathon.features.jabber;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jabber")
@RequiredArgsConstructor
public class JabberController {

    private final JabberStatusService jabberStatusService;

    @GetMapping("/status")
    public List<JabberServerStatus> status() {
        return jabberStatusService.statuses();
    }
}
