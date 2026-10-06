package nl.nulldevelopment.nfcappblocker.testing

import nl.nulldevelopment.nfcappblocker.util.DefaultNameFormats

class FakeDefaultNameFormats : DefaultNameFormats {
    override fun tag(number: Int) = "Tag $number"
    override fun profile(number: Int) = "Profile $number"
}
