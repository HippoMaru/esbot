package com.hippomaru.esbot.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public interface RandomCatService {
    public ByteArrayInputStream getRandomCatImage() throws IOException;
}
