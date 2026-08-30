package com.tkisor.nekojs.api.contract;

import com.tkisor.nekojs.api.surface.ApiVersion;

import java.net.URI;
import java.util.Objects;

/**
 * 已完成校验的 API 契约，把契约内容与其来源、完整性/兼容性哈希绑定在一起。
 *
 * <p>不可变；通过 {@link #create(ApiContractIdentity, NormativeApiContract, URI, String, String, String)}
 * 构造。完整性哈希用于校验契约资源未被篡改，兼容性哈希用于契约版本比对。
 */
public final class VerifiedApiContract {

    private final ApiContractIdentity identity;
    private final NormativeApiContract contract;
    private final URI codeSource;
    private final String resourceName;
    private final String integritySha256;
    private final String compatibilitySha256;

    VerifiedApiContract(ApiContractIdentity identity, NormativeApiContract contract,
                        URI codeSource, String resourceName,
                        String integritySha256, String compatibilitySha256) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.contract = Objects.requireNonNull(contract, "contract");
        this.codeSource = Objects.requireNonNull(codeSource, "codeSource");
        this.resourceName = Objects.requireNonNull(resourceName, "resourceName");
        this.integritySha256 = Objects.requireNonNull(integritySha256, "integritySha256");
        this.compatibilitySha256 = Objects.requireNonNull(compatibilitySha256, "compatibilitySha256");
    }

    /** 构造一个已验证契约；全部参数不能为 {@code null}。 */
    public static VerifiedApiContract create(ApiContractIdentity identity, NormativeApiContract contract,
                                             URI codeSource, String resourceName,
                                             String integritySha256, String compatibilitySha256) {
        return new VerifiedApiContract(identity, contract, codeSource, resourceName, integritySha256, compatibilitySha256);
    }

    /** 返回契约身份。 */
    public ApiContractIdentity identity() {
        return identity;
    }

    /** 返回契约内容。 */
    public NormativeApiContract contract() {
        return contract;
    }

    /** 返回契约资源来源 URI。 */
    public URI codeSource() {
        return codeSource;
    }

    /** 返回契约资源名。 */
    public String resourceName() {
        return resourceName;
    }

    /** 返回契约资源的 SHA-256 完整性哈希。 */
    public String integritySha256() {
        return integritySha256;
    }

    /** 返回契约的 SHA-256 兼容性哈希。 */
    public String compatibilitySha256() {
        return compatibilitySha256;
    }
}
