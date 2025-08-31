/**********************************
 *@项目名称: broker-parent
 *@文件名称: io.btg.gts.common.entity
 *@Date 2018/6/21
 *@Author toyoda
 *@Copyright（C）: 2018 BitCloudGroup Inc.   All rights reserved.
 *注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
 ***************************************/
package com.earth.grpc.context;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrpcHeader {

    private String remoteIp;

    private Long userId;

    private Long orgId;

    private String language;

    private String requestId;

    private Long requestTime;

    private String uuid;

    /**
     * 此参数通过 http 请求头获取 ，参数为 tag = v2.0.0
     * <p>
     * 则会路由到指定版本
     */
    private String tag;

}
