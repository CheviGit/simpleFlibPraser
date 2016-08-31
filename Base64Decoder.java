package com.company.parser.local;

class Base64Decoder {
	private static byte[] charTable = new byte[256];
	private static boolean[] isValidSymbol = new boolean[256];
	
	static {
        for (int index = 0; index < 256; index++) {
            charTable[index] = -1;
            isValidSymbol[index] = false;
        }
        for (int index = 'A'; index <= 'Z'; index++) {
            charTable[index] = (byte) (index - 'A');
            isValidSymbol[index] = true;
        }
        for (int index = 'a'; index <= 'z'; index++) {
            charTable[index] = (byte) (index - 'a' + 26);
            isValidSymbol[index] = true;
        }
        for (int index = '0'; index <= '9'; index++) {
            charTable[index] = (byte) (index - '0' + 52);
            isValidSymbol[index] = true;
        }
        charTable['+'] = 62;
        isValidSymbol['+'] = true;
        charTable['/'] = 63;
        isValidSymbol['/'] = true;
        isValidSymbol['='] = true;
	}

	public static byte[] decode(byte[] input) {
		byte[] data = purgeNonBase64(input);
		if (data.length == 0)
			return null;
        int numberQuadruple = data.length / 4;
        byte output[] = null;
        byte b1 = 0, b2 = 0, b3 = 0, b4 = 0, marker0 = 0, marker1 = 0;

        int outIndex = 0;
        int inIndex = 0;
        {
            int lastData = data.length;
            while (data[lastData - 1] == '=') {
                if (--lastData == 0) {
                    return new byte[0];
                }
            }
            output = new byte[lastData - numberQuadruple];
        }

        for (int i = 0; i < numberQuadruple; i++) {
            inIndex = i * 4;
            marker0 = data[inIndex + 2];
            marker1 = data[inIndex + 3];

            b1 = charTable[data[inIndex]];
            b2 = charTable[data[inIndex + 1]];

            if (marker0 != '=' && marker1 != '=') {
                //No PAD e.g 3cQl
                b3 = charTable[marker0];
                b4 = charTable[marker1];

                output[outIndex] = (byte) (b1 << 2 | b2 >> 4);
                output[outIndex + 1] =
                    (byte) (((b2 & 0xf) << 4) | ((b3 >> 2) & 0xf));
                output[outIndex + 2] = (byte) (b3 << 6 | b4);
            } else if (marker0 == '=') {
                output[outIndex] = (byte) (b1 << 2 | b2 >> 4);
            } else if (marker1 == '=') {
                b3 = charTable[marker0];

                output[outIndex] = (byte) (b1 << 2 | b2 >> 4);
                output[outIndex + 1] =
                    (byte) (((b2 & 0xf) << 4) | ((b3 >> 2) & 0xf));
            }
            outIndex += 3;
        }
        return output;
	}

	private static byte[] purgeNonBase64(byte[] input) {
        byte clean[] = new byte[input.length];
        int counter = 0;
        for (int i = 0; i < input.length; i++) {
            if (isValidSymbol[input[i]]) {
                clean[counter++] = input[i];
            }
        }
        byte output[] = new byte[counter];
        System.arraycopy(clean, 0, output, 0, counter);
        return output;
	}
}
