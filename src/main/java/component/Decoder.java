package component;

import dto.InputDecoded;
import dto.InputEncoded;

import java.util.Optional;

public interface Decoder {
    Optional<InputDecoded> decode(InputEncoded code);
}
