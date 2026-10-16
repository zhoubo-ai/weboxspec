# Test image source

`blue-tile.webp` is the unchanged small WebP decoder fixture from the TwelveMonkeys ImageIO project: [original file](https://github.com/haraldk/TwelveMonkeys/blob/master/imageio/imageio-webp/src/test/resources/webp/blue_tile.webp). Project [BSD license](https://github.com/haraldk/TwelveMonkeys/blob/master/LICENSE.txt). Downloaded 2026-10-16; SHA-256 `52d084cc53e448ec130ad275f7994d16b2b22e98dce22b9cea97db3c63a0a67c`. Used only by integration tests; not a menu asset.

- `expanding-noise.webp`: original synthetic test fixture, deterministic Python random seed 6082, 1600×1600 RGB noise, Pillow WebP quality 55/method 4. Tests a genuine sub-5-MiB WebP whose decoded PNG exceeds 5 MiB. SHA-256: `79b65b0a76e95f5da6cca18211e01bb85593cdbbb21dc5bc6baf27fce99b1b34`. Not menu imagery.
