package com.zhyf.decoction.api.legacy;

import com.zhyf.common.api.ApiResponse;
import com.zhyf.decoction.adapter.MesAdapter;
import com.zhyf.decoction.application.DecoctionRecords;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyMesProtocolController {

    private final MesAdapter mesAdapter;

    public LegacyMesProtocolController(MesAdapter mesAdapter) {
        this.mesAdapter = mesAdapter;
    }

    @RequestMapping(path = "/getWaterByPailNo", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> getWaterByPailNo(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiResponse.ok(mesAdapter.getWaterByPailNo(request(query, body).pailNo()));
    }

    @RequestMapping(path = "/waterEndNotice", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<DecoctionRecords.DecoctionTaskEventRecord> waterEndNotice(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiResponse.ok(mesAdapter.waterEndNotice(request(query, body).toOperation("LEGACY-MES-WATER")));
    }

    @RequestMapping(path = "/getRecipeInfoById", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> getRecipeInfo(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiResponse.ok(mesAdapter.getRecipeInfo(request(query, body).toOperation("LEGACY-MES-RECIPE")));
    }

    @RequestMapping(path = "/recipeBoilInfoCallback", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<DecoctionRecords.DecoctionTaskEventRecord> recipeBoilInfoCallback(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiResponse.ok(mesAdapter.recipeBoilInfoCallback(request(query, body).toOperation("LEGACY-MES-BOIL")));
    }

    @RequestMapping(path = "/reviewFinishPushRecipe", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<List<DecoctionRecords.PrescriptionRecord>> reviewFinishPushRecipe(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        LegacyDeviceRequest request = request(query, body);
        return ApiResponse.ok(mesAdapter.reviewFinishPushRecipe(request.prescriptionNo(), request.limit(50)));
    }

    @RequestMapping(path = "/decoctingStatusNotice", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse<DecoctionRecords.DecoctionTaskRecord> decoctingStatusNotice(
            @RequestParam Map<String, String> query,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return ApiResponse.ok(mesAdapter.decoctingStatusNotice(request(query, body).toOperation("LEGACY-MES-STATUS")));
    }

    private LegacyDeviceRequest request(Map<String, String> query, Map<String, Object> body) {
        return LegacyDeviceRequest.from(query, body);
    }
}
