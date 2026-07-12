package com.lumenglover.yuemupicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 扫码请求
 */
@Data
public class QrLoginScanRequest implements Serializable {

    /**
     * 二维码 token
     */
    private String qrToken;

    private static final long serialVersionUID = 1L;
}
