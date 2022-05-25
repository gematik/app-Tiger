import BannerType from "@/types/BannerType";

export default class BannerMessage {
  text: string | null;
  color: string |null;
  type: BannerType;

  constructor() {
    this.text = "";
    this.color = "";
    this.type = BannerType.MESSAGE;
  }

  public static fromJson(json: {  bannerMessage:string, bannerColor:string, bannerType: string } ) : BannerMessage {
    const msg:BannerMessage = new BannerMessage();
    msg.text = json.bannerMessage;
    msg.color = json.bannerColor;
    if (json.bannerType) {
      msg.type = json.bannerType as BannerType;
    }
    return msg;
  }
}
