package jar.controller;
import org.
@RestController


@
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class usercontroller {

@GetMapping("/get")
    public String get(){
        return "login succesful welcome to the user controller";


    }
    }



    