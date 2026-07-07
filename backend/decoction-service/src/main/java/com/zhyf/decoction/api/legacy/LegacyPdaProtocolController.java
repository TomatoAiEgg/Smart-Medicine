package com.zhyf.decoction.api.legacy;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.decoction.adapter.DeviceAdapter;
import com.zhyf.decoction.application.DecoctionRecords;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyPdaProtocolController {

    private final DeviceAdapter deviceAdapter;

    public LegacyPdaProtocolController(DeviceAdapter deviceAdapter) {
        this.deviceAdapter = deviceAdapter;
    }

    @RequestMapping(path = "/pdaUserLogin", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<DecoctionRecords.PdaLoginResult> login(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiResponse.ok(deviceAdapter.login(request(query, body).toOperation("LEGACY-PDA-LOGIN")));
    }

    @RequestMapping(path = "/pdaGetDecoctEquip", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<List<DecoctionRecords.DeviceRecord>> listDevices() {
        return ApiResponse.ok(deviceAdapter.listDecoctionDevices());
    }

    @RequestMapping(path = "/pdaGetCanOpRecipeList", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<List<DecoctionRecords.PrescriptionRecord>> listCanOperatePrescriptions(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        LegacyDeviceRequest request = request(query, body);
        return ApiResponse.ok(deviceAdapter.listCanOperatePrescriptions(request.limit(50)));
    }

    @RequestMapping(path = "/pdaBindPrescription", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> bindPrescription(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiResponse.ok(deviceAdapter.bindPrescription(request(query, body).toOperation("LEGACY-PDA-BIND", "0")));
    }

    @RequestMapping(path = "/pdaOpDecoctStatus", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> reportDecoctStatus(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiResponse.ok(deviceAdapter.reportPdaStatus(request(query, body).toOperation("LEGACY-PDA-STATUS")));
    }

    private LegacyDeviceRequest request(Map<String, String> query, Map<String, Object> body) {
        return LegacyDeviceRequest.from(query, body);
    }
}
