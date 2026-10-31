# 1. Authentication and authorization: start with one login

Li opens the Omni-Stack home page and sees the project Wiki. He can read it without signing in. When he needs to handle a purchase requisition, he must establish his identity and enter a workspace that his account may use.

This chapter follows that story: how the system recognizes a person, remembers a login, and decides why a signed-in user may still lack a button. The first pages do not require Java knowledge. Read the concepts before the framework and project source.

## Follow one complete visit

Li submits his account, password, and CAPTCHA. Once the authentication service accepts them, it issues an access credential. Later requests carry that credential through the gateway. Business services then check both the permitted operation and the records Li may access.

For example, Li may sign in to purchasing without being allowed to approve his own requisition. A manager may approve requests within their department without gaining access to another company's data. Login, functional permissions, and data scope answer different questions.

The public Wiki does not become a different article after login. The account area changes: eligible identities can use it to enter the workspace or console.

## Separate three questions

| Question | What the system does | Everyday example |
|---|---|---|
| Who is this person? | Authentication | Reception verifies a visitor's identity |
| What may this person do? | Authorization | A visitor pass permits entry to agreed floors |
| Which records may this person see? | Data scope control | A department manager handles requests within their responsibility |

Repeatedly entering a password rarely fixes a permission error. Password verification establishes identity; the account's role, organization, and business ownership must satisfy their own conditions.

Menus and visible buttons help people operate the application. The server must still authorize the request. Hiding a button does not protect an API by itself.

## How the system remembers a login

Two terms appear frequently: session and token.

A session is a login record kept by the server. A browser usually sends a small reference in a Cookie, which lets the server find that record. The Cookie is not the entire identity record, and it does not let the browser choose who the server trusts.

A token is a credential that later requests can carry. Ordinary password login in Omni-Stack returns a JWT: a signed token containing identity claims. The signature helps detect changes to its contents; it does not encrypt those contents.

Treat a token like an access card. A public Wiki does not mean any token can read all business data.

## Why both sessions and tokens appear

Ordinary password login and enterprise SSO follow different paths.

The ordinary entry checks the account, password, and CAPTCHA, then issues an access token. The authorization server used by enterprise SSO must first recognize the person using the browser before it can issue an authorization code and complete the token exchange. That requires a server-side session here.

Remember their project responsibilities: the session identifies the browser's login state to the authorization server; the access token accompanies later business requests. Obtaining one does not automatically create the other.

The next page explains authorization codes and PKCE through four participants and a diagram. There is no need to memorize the abbreviations first.

## Continue in this order

| Page | What you will be able to explain |
|---|---|
| [2. Design](authentication-design.en.md) | Why an application should not keep a user's password, and what authorization codes and PKCE solve |
| [3. Framework and source](authentication-framework.en.md) | Why the project uses Spring Security and its authorization server; how requests, exchange material, and code consumption are handled |
| [4. Ordinary password login](authentication-password.en.md) | How the project checks CAPTCHA and passwords, loads roles, and issues credentials |
| [5. Enterprise SSO](authentication-oauth2.en.md) | How sessions, authorization codes, PKCE, and the browser callback form a login |
| [6. Device and social login](authentication-device-social.en.md) | How the participants and boundaries differ, and how registration and the supplier portal connect |
| [7. Permissions, logout, and troubleshooting](authentication-security.en.md) | Why a signed-in person may still be denied, and where to investigate |

Each source excerpt is followed by an explanation of its responsibility and result. Optional references are provided when you want the original material; understanding the chapter does not require opening repository code pages.
