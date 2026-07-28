package com.teaching.backend.domain.material.service.extract;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public interface HostAddressResolver {

    List<InetAddress> resolve(String host) throws UnknownHostException;
}
