package com.comp90018.deadline.feature.account

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.domain.repository.Account
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun show(
        page: AccountPage,
        account: Account = Account(),
        action: AccountActionState = AccountActionState(),
        home: () -> Unit = {
        },
    ) {
        compose.setContent {
            DeadlineTheme {
                AccountContent(page, account, action, home, {}, {}, { _, _ -> }, { _, _, _, _ -> }, {}, {}, {})
            }
        }
    }

    @Test fun guestCanContinueWithoutCredentials() {
        var continued = false
        show(AccountPage.LOGIN, home = { continued = true })
        compose.onNodeWithText("Continue as Guest").performScrollTo().performClick()
        assertTrue(continued)
    }

    @Test fun registeredProfileShowsNicknameAndEmail() {
        show(AccountPage.PROFILE, Account("uid", "private@example.test", "Student"))
        compose.onNodeWithText("Registered account").assertIsDisplayed()
        compose.onNodeWithText("private@example.test").assertIsDisplayed()
        compose.onNodeWithText("Student").assertIsDisplayed()
    }

    @Test fun submissionDisablesDuplicateRegistration() {
        show(AccountPage.REGISTER, action = AccountActionState(busy = true))
        compose.onNodeWithText("Confirm password").assertIsNotEnabled()
        compose.onNodeWithText("Continue as Guest").assertIsNotEnabled()
    }

    @Test fun errorsRemainVisibleWithoutForcingLogin() {
        show(AccountPage.LOGIN, action = AccountActionState(error = "The email or password is incorrect."))
        compose.onNodeWithText("The email or password is incorrect.").assertIsDisplayed()
        compose.onNodeWithText("Continue as Guest").performScrollTo().assertIsDisplayed()
    }

    @Test fun pendingSyncDoesNotDisableContinuePlaying() {
        show(
            AccountPage.PROFILE,
            Account("uid", "private@example.test", "Student"),
            AccountActionState(succeeded = true, profilePending = true, progressPending = true),
        )
        compose.onNodeWithText("Continue Playing").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Retry Account Sync").performScrollTo().assertIsEnabled()
    }

    @Test fun passwordVisibilityCanBeToggled() {
        show(AccountPage.LOGIN)
        compose.onNodeWithText("Password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("Show").performClick()
        compose.onNodeWithText("Password").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Password))
        compose.onNodeWithText("Hide").performClick()
        compose.onNodeWithText("Password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }
}
