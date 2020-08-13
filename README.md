# Ardor Gifts

Contract and example interface to create gifts on Ardor.

Contract Runner setup example:

{
  "accountRS": "ARDOR-64L4-C4H9-Z9PU-9YKDT",
  "autoFeeRate": true,
  "validator": false,
  "params": {"Gift": 
    {
      "secretPhrase":"SECRETPHRASE"
     }
    }
}

To create a gift, anyone would just send any amount of tokens to the contract and it will send back 2 tokens/codes
The first is the redeem token and the second is a view token

6f319o4v5psiv2kvp60t9jkd397i9b9knp0gira0futrvq61u8tl2sv9ej46me84m7i0vcmap5f6223aep3vecs456jnvoh5prkd13lr9lc0umo89qnbe9nlu84v1pd3k5ak55qq0l7h16kcv6q4kpmd6l63269g|||6f319o4v5psiv2kvp60t9jkd397i9b9knp0gira0futrvq61u8tl2sv9h9ub2lo4gi3gsa0aeufsir04rqo1rv6eiv23mqdpf66h4s1f2ddgussbsrjecgorj0rpv8aaiheqtg6kqsg1siiuervqubcnh5g656fc

An example of what is sent for viewing the status of a gift:

{ "token":"none", "tokenView":"6f319o4v5psiv2kvp60t9jkd397i9b9knp0gira0futrvq61u8tl2sv9h9ub2lo4gi3gsa0aeufsir04rqo1rv6eiv23mqdpf66h4s1f2ddgussbsrjecgorj0rpv8aaiheqtg6kqsg1siiuervqubcnh5g656fc","recipient":"ARDOR-L84D-3YV4-TASR-GJHHK", "giftAmount":"150000000","giftType":"2","tokenType":"view"}

To redeem you would just fill in `token` with the valid redeem token