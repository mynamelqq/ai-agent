package com.ai.aigent.utils;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sts.v20180813.StsClient;
import com.tencentcloudapi.sts.v20180813.models.AssumeRoleRequest;
import com.tencentcloudapi.sts.v20180813.models.AssumeRoleResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CosStsUtil {

    @Value("${tencent.secret-id}")
    private String secretId;

    @Value("${tencent.secret-key}")
    private String secretKey;

    @Value("${tencent.role-arn}")
    private String roleArn;

    @Value("${tencent.cos.bucket}")
    private String bucket;

    @Value("${tencent.cos.region}")
    private String region;

   public  AssumeRoleResponse getCredential() throws Exception {

        Credential cred = new Credential(secretId, secretKey);

        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("sts.tencentcloudapi.com");

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        StsClient client = new StsClient(cred, "ap-nanjing", clientProfile);

        String policy = """
        {
           "version": "2.0",
           "statement": [
             {
               "effect": "allow",
               "action": [
                 "name/cos:PutObject",
                 "name/cos:PostObject"
               ],
               "resource": [
                 "qcs::cos:ap-nanjing:uid/100046213466:efrewew-1402637531/*"
               ]
             }
           ]
         }
        """.formatted(region, bucket);
        AssumeRoleRequest req = new AssumeRoleRequest();
        req.setRoleArn(roleArn);            // 角色 ARN
        req.setRoleSessionName("ai-upload");
        req.setPolicy(policy);          // 临时策略 JSON
        req.setDurationSeconds(1800L);

        return client.AssumeRole(req);
    }
}
