package castofo_nower.com.co.nower.models;

import java.util.Map;
import java.util.TreeMap;


public class User {

  public static int id;
  public static String email;
  public static String name;
  public static String gender;
  public static String birthday;

  public static Map<Integer, Redemption> takenPromos;

  public static void setUserData(int idToSet, String emailToSet,
                                 String nameToSet, String genderToSet,
                                 String birthdayToSet,
                                 Map<Integer, Redemption> takenPromosToSet) {
    id = idToSet;
    email = emailToSet;
    name = nameToSet;
    gender = genderToSet;
    birthday = birthdayToSet;
    takenPromos = takenPromosToSet;
  }

  public static void addPromoToTakenPromos(Integer promoId,
                                           Redemption redemption) {
    if (takenPromos == null) takenPromos = new TreeMap<>();
    takenPromos.put(promoId, redemption);
  }

}
