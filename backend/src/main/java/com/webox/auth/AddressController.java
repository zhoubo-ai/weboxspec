package com.webox.auth;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class AddressController {
    private final AddressRepository addresses;
    public AddressController(AddressRepository addresses) { this.addresses=addresses; }
    @GetMapping("/api/v1/me/addresses") public List<AddressRepository.Address> list(@AuthenticationPrincipal UserIdentity user) { return addresses.list(user.userId()); }
}
